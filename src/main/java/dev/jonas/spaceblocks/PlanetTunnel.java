package dev.jonas.spaceblocks;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in construction of both connected halves of a lined tunnel, with bounded per-tick work. */
public final class PlanetTunnel {
  private static final TicketType<ChunkPos> TICKET =
      TicketType.create("spaceblocks_tunnel", Comparator.comparingLong(ChunkPos::toLong));
  private static final Map<ServerLevel, Build> builds = new WeakHashMap<>();

  public static boolean isBuilding(ServerLevel level) {
    return builds.containsKey(level);
  }

  private static final class Build {
    final ServerPlayer player;
    final ServerLevel level;
    final Planet planet;
    final int x, z, opposite;
    final Set<ChunkPos> tickets = new HashSet<>();
    int top, index;
    boolean ready;

    Build(ServerPlayer p) {
      player = p;
      level = p.serverLevel();
      planet = Planet.of(level);
      x = PeriodicMath.wrap(p.blockPosition().getX(), planet.size());
      z = PeriodicMath.wrap(p.blockPosition().getZ(), planet.size());
      opposite = PeriodicMath.wrap(x + planet.size() / 2, planet.size());
      for (int cx : new int[] {x, opposite})
        for (int dx = -2; dx <= 2; dx += 4)
          for (int dz = -2; dz <= 2; dz += 4)
            tickets.add(new ChunkPos(planet.chunk((cx + dx) >> 4), planet.chunk((z + dz) >> 4)));
      for (var c : tickets) level.getChunkSource().addRegionTicket(TICKET, c, 0, c);
    }

    void release() {
      for (var c : tickets) level.getChunkSource().removeRegionTicket(TICKET, c, 0, c);
    }
  }

  public static int create(ServerPlayer p) {
    if (Planet.of(p.level()) == null) {
      p.sendSystemMessage(Component.literal("Enter a planet first."));
      return 0;
    }
    if (builds.containsKey(p.serverLevel())) {
      p.sendSystemMessage(Component.literal("A tunnel is already being built."));
      return 0;
    }
    builds.put(p.serverLevel(), new Build(p));
    p.sendSystemMessage(
        Component.literal(
            "Creating two connected, glass-lined shafts at your X/Z and half a map away. Blocks in"
                + " those columns will be replaced."));
    return 1;
  }

  public static int drop(ServerPlayer p) {
    if (Planet.of(p.level()) == null) return 0;
    var s = PlanetSettings.get(p.level());
    if (!s.hasTunnel || builds.containsKey(p.serverLevel())) {
      p.sendSystemMessage(Component.literal("Create the tunnel first: /planet tunnel create"));
      return 0;
    }
    s.fallthrough = true;
    s.realisticGravity = true;
    s.airDrag = false;
    s.centrifugal = false;
    s.setDirty();
    for (var player : p.serverLevel().players()) PlanetNetwork.sync(player);
    p.getAbilities().mayfly = true;
    p.getAbilities().flying = false;
    p.onUpdateAbilities();
    p.connection.teleport(
        s.tunnelX + .5, s.tunnelTop - 2, s.tunnelZ + .5, p.getYRot(), p.getXRot());
    p.setDeltaMovement(Vec3.ZERO);
    p.fallDistance = 0;
    PlanetServer.reset(p);
    p.sendSystemMessage(
        Component.literal(
            "Tunnel fall started. Gravity varies with altitude; vertical air drag is off. Bottom"
                + " passage uses Spheretest's half-map shift and velocity reversal."));
    return 1;
  }

  public static void tick(ServerTickEvent.Post event) {
    var pos = new BlockPos.MutableBlockPos();
    for (var iterator = builds.values().iterator(); iterator.hasNext(); ) {
      var b = iterator.next();
      if (b.player.hasDisconnected() || b.player.level() != b.level) {
        b.release();
        iterator.remove();
        continue;
      }
      if (!b.ready) {
        boolean loaded = true;
        for (var c : b.tickets)
          if (b.level.getChunkSource().getChunkNow(c.x, c.z) == null) loaded = false;
        if (!loaded) continue;
        int surface = Planet.SURFACE;
        if (b.level.getChunkSource().getGenerator() instanceof PlanetGenerator g && g.natural) {
          var terrain =
              new PeriodicTerrain(
                  b.planet.size(), g.terrainSeed(b.level.getChunkSource().randomState()));
          surface =
              Math.max(
                  surface, Math.max(terrain.surface(b.x, b.z), terrain.surface(b.opposite, b.z)));
        }
        b.top =
            Math.min(
                1000,
                Math.max(
                        surface,
                        Math.max(
                            b.level.getHeight(Heightmap.Types.WORLD_SURFACE, b.x, b.z),
                            b.level.getHeight(Heightmap.Types.WORLD_SURFACE, b.opposite, b.z)))
                    + 16);
        b.ready = true;
      }
      long deadline = System.nanoTime() + 2_000_000;
      int layers = b.top - (b.planet.bottom() - 4) + 1, total = layers * 50;
      for (int budget = 0; budget < 512 && b.index < total; budget++) {
        int i = b.index++,
            end = i / (layers * 25),
            cell = i % 25,
            y = b.planet.bottom() - 4 + (i / 25) % layers;
        int dx = cell % 5 - 2, dz = cell / 5 - 2, x = end == 0 ? b.x : b.opposite;
        var state =
            (Math.abs(dx) == 2 || Math.abs(dz) == 2)
                ? Blocks.GLASS.defaultBlockState()
                : Blocks.AIR.defaultBlockState();
        b.level.setBlock(
            pos.set(
                PeriodicMath.wrap(x + dx, b.planet.size()),
                y,
                PeriodicMath.wrap(b.z + dz, b.planet.size())),
            state,
            3);
        if (System.nanoTime() > deadline) break;
      }
      if (b.index == total) {
        var s = PlanetSettings.get(b.level);
        s.hasTunnel = true;
        s.tunnelX = b.x;
        s.tunnelZ = b.z;
        s.tunnelTop = b.top;
        s.fallthrough = true;
        s.realisticGravity = true;
        s.airDrag = false;
        s.centrifugal = false;
        s.setDirty();
        for (var p : b.level.players()) PlanetNetwork.sync(p);
        b.player.sendSystemMessage(
            Component.literal(
                "Tunnel ready. Use /planet tunnel drop. Other end: X="
                    + b.opposite
                    + ", Z="
                    + b.z
                    + ". /planet surface returns to an exit."));
        b.release();
        iterator.remove();
      }
    }
  }

  public static void stopped(ServerStoppedEvent event) {
    builds.clear();
  }
}
