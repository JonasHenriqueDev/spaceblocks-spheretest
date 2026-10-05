package dev.jonas.spaceblocks;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** On-demand sampled atlas. Forecasting never loads chunks; confirmed samples use getChunkNow. */
public final class PlanetAtlas {
  public static final int RESOLUTION = 96;
  public static final int[] BIOME_COLORS = {
    0x236BA5, 0x7DAA58, 0x477B44, 0xD6BE7B, 0x527F70, 0xD6E5E8, 0x8B9098
  };

  public record Request(boolean hud) implements CustomPacketPayload {
    public static final Type<Request> TYPE =
        new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                SpaceBlocks.MOD_ID, "atlas_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of((b, p) -> b.writeBoolean(p.hud), b -> new Request(b.readBoolean()));

    public Request() {
      this(false);
    }

    public Type<Request> type() {
      return TYPE;
    }
  }

  public record Snapshot(
      String dimension,
      int radius,
      int bottom,
      int resolution,
      double playerX,
      double playerZ,
      int[] heights,
      int[] colors,
      boolean[] confirmed)
      implements CustomPacketPayload {
    public Snapshot {
      if (radius < 32
          || radius > 1024
          || resolution < 16
          || resolution > RESOLUTION
          || !Double.isFinite(playerX)
          || !Double.isFinite(playerZ)
          || heights.length != resolution * resolution
          || colors.length != heights.length
          || confirmed.length != heights.length)
        throw new IllegalArgumentException("Invalid planet atlas");
      heights = heights.clone();
      colors = colors.clone();
      confirmed = confirmed.clone();
    }

    public int size() {
      return PeriodicMath.circumference(radius);
    }

    public int confirmedCount() {
      int count = 0;
      for (boolean flag : confirmed) if (flag) count++;
      return count;
    }

    public static final Type<Snapshot> TYPE =
        new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                SpaceBlocks.MOD_ID, "atlas_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeUtf(p.dimension, 128);
              b.writeVarInt(p.radius);
              b.writeInt(p.bottom);
              b.writeVarInt(p.resolution);
              b.writeDouble(p.playerX);
              b.writeDouble(p.playerZ);
              for (int i = 0; i < p.heights.length; i++) {
                b.writeShort(p.heights[i]);
                b.writeInt(p.colors[i]);
                b.writeBoolean(p.confirmed[i]);
              }
            },
            b -> {
              String dimension = b.readUtf(128);
              int radius = b.readVarInt(), bottom = b.readInt(), n = b.readVarInt();
              if (n < 16 || n > RESOLUTION)
                throw new IllegalArgumentException("Atlas resolution out of bounds");
              double x = b.readDouble(), z = b.readDouble();
              int[] heights = new int[n * n], colors = new int[n * n];
              boolean[] confirmed = new boolean[n * n];
              for (int i = 0; i < heights.length; i++) {
                heights[i] = b.readShort();
                colors[i] = b.readInt();
                confirmed[i] = b.readBoolean();
              }
              return new Snapshot(dimension, radius, bottom, n, x, z, heights, colors, confirmed);
            });

    public Type<Snapshot> type() {
      return TYPE;
    }
  }

  public record HudSnapshot(Snapshot data) implements CustomPacketPayload {
    public static final Type<HudSnapshot> TYPE = new Type<>(SpaceBlocks.id("hud_atlas"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HudSnapshot> CODEC =
        StreamCodec.of(
            (b, p) -> Snapshot.CODEC.encode(b, p.data),
            b -> new HudSnapshot(Snapshot.CODEC.decode(b)));

    public Type<HudSnapshot> type() {
      return TYPE;
    }
  }

  private static final Map<ServerPlayer, Boolean> hudRequests = new WeakHashMap<>();
  private static final Map<ServerPlayer, Snapshot> recent = new WeakHashMap<>();

  private static final class Scan {
    final Snapshot data;
    int index;

    Scan(Snapshot data) {
      this.data = data;
    }
  }

  private static final Map<ServerPlayer, Long> requested = new WeakHashMap<>();
  private static final Map<ServerPlayer, CompletableFuture<Snapshot>> forecasts =
      new WeakHashMap<>();
  private static final Map<ServerPlayer, Scan> scans = new WeakHashMap<>();

  public static int coordinate(int sample, int n, int size) {
    return -size / 2 + (int) ((long) sample * size / n);
  }

  public static void request(ServerPlayer player) {
    request(player, false);
  }

  public static void request(ServerPlayer player, boolean hud) {
    var planet = Planet.of(player.level());
    if (planet == null) {
      player.sendSystemMessage(Component.literal("Enter a planet before opening its map."));
      return;
    }
    long tick = player.server.getTickCount();
    if (forecasts.containsKey(player) || scans.containsKey(player)) {
      // A manual map request takes precedence over a pending passive HUD refresh.
      if (!hud) hudRequests.put(player, false);
      return;
    }
    if (tick - requested.getOrDefault(player, -100L) < 40) {
      var cached = recent.get(player);
      if (!hud
          && cached != null
          && cached.dimension.equals(player.level().dimension().location().toString()))
        PacketDistributor.sendToPlayer(player, cached);
      return;
    }
    if (forecasts.size() + scans.size() >= 2) {
      if (!hud) player.sendSystemMessage(Component.literal("Planet map busy. Try again shortly."));
      return;
    }
    requested.put(player, tick);
    hudRequests.put(player, hud);
    var level = player.serverLevel();
    long seed =
        ((PlanetGenerator) level.getChunkSource().getGenerator())
            .terrainSeed(level.getChunkSource().randomState());
    boolean natural =
        level.getChunkSource().getGenerator() instanceof PlanetGenerator g && g.natural;
    String dimension = level.dimension().location().toString();
    var nativeTerrain =
        level.getChunkSource().getGenerator() instanceof PlanetGenerator generator
            ? generator.nativeTerrain
            : null;
    double px = player.getX(), pz = player.getZ();
    if (!hud)
      player.sendSystemMessage(
          Component.literal("Preparing planet map without generating chunks..."));
    forecasts.put(
        player,
        CompletableFuture.supplyAsync(
            () -> {
              int n = RESOLUTION;
              int[] heights = new int[n * n], colors = new int[n * n];
              boolean[] confirmed = new boolean[n * n];
              var terrain = new PeriodicTerrain(planet.size(), seed);
              for (int z = 0; z < n; z++)
                for (int x = 0; x < n; x++) {
                  int wx = coordinate(x, n, planet.size()),
                      wz = coordinate(z, n, planet.size()),
                      i = z * n + x;
                  heights[i] = natural ? Math.max(64, terrain.surface(wx, wz)) : 64;
                  colors[i] = natural ? BIOME_COLORS[terrain.biome(wx, wz)] : BIOME_COLORS[1];
                  if (nativeTerrain != null) {
                    if (nativeTerrain.type == PlanetType.FLAT) heights[i] = 64;
                    colors[i] =
                        nativeTerrain.type.polar(wz, planet.size(), nativeTerrain.poles)
                            ? 0xD6E5E8
                            : switch (nativeTerrain.type) {
                              case DESERT -> 0xD6BE7B;
                              case JUNGLE -> 0x377B34;
                              case MUSHROOM -> 0x957C89;
                              case DIRT -> 0x90694C;
                              case STONE -> 0x8B9098;
                              case NETHER -> 0x933A32;
                              case FLAT -> 0x7DAA58;
                              default -> colors[i];
                            };
                  }
                }
              return new Snapshot(
                  dimension,
                  planet.radius(),
                  planet.bottom(),
                  n,
                  px,
                  pz,
                  heights,
                  colors,
                  confirmed);
            },
            net.minecraft.Util.backgroundExecutor()));
  }

  public static void tick(ServerTickEvent.Post event) {
    for (var iterator = forecasts.entrySet().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      var player = entry.getKey();
      if (player.hasDisconnected()) {
        iterator.remove();
        continue;
      }
      if (!entry.getValue().isDone()) continue;
      try {
        var data = entry.getValue().join();
        if (data.dimension.equals(player.level().dimension().location().toString()))
          scans.put(player, new Scan(data));
      } catch (Exception ex) {
        SpaceBlocks.LOGGER.error("Planet atlas failed", ex);
      }
      iterator.remove();
    }
    var pos = new BlockPos.MutableBlockPos();
    for (var iterator = scans.entrySet().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      var player = entry.getKey();
      var scan = entry.getValue();
      var data = scan.data;
      if (player.hasDisconnected()
          || !data.dimension.equals(player.level().dimension().location().toString())) {
        iterator.remove();
        continue;
      }
      var level = player.serverLevel();
      long deadline = System.nanoTime() + 1_500_000;
      for (int count = 0; count < 384 && scan.index < data.heights.length; count++) {
        int i = scan.index++,
            wx = coordinate(i % data.resolution, data.resolution, data.size()),
            wz = coordinate(i / data.resolution, data.resolution, data.size());
        var chunk = level.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
        if (chunk != null) {
          int h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
          var state = chunk.getBlockState(pos.set(wx, h, wz));
          data.heights[i] = h;
          data.colors[i] = state.getMapColor(level, pos).col;
          data.confirmed[i] = true;
        }
        if (System.nanoTime() > deadline) break;
      }
      if (scan.index == data.heights.length) {
        recent.put(player, data);
        if (Boolean.TRUE.equals(hudRequests.remove(player)))
          PacketDistributor.sendToPlayer(player, new HudSnapshot(data));
        else PacketDistributor.sendToPlayer(player, data);
        iterator.remove();
      }
    }
    requested.keySet().removeIf(ServerPlayer::hasDisconnected);
    recent.keySet().removeIf(ServerPlayer::hasDisconnected);
    hudRequests.keySet().removeIf(ServerPlayer::hasDisconnected);
  }

  public static void stopped(ServerStoppedEvent event) {
    requested.clear();
    hudRequests.clear();
    recent.clear();
    forecasts.clear();
    scans.clear();
  }
}
