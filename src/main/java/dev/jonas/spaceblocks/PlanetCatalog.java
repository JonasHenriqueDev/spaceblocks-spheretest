package dev.jonas.spaceblocks;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Saved, independent planets backed by reserved dimension IDs. Never regenerates an occupied slot.
 */
public final class PlanetCatalog extends SavedData {
  public static final int SLOTS = 16;

  public record Entry(
      String name,
      String dimension,
      int radius,
      long seed,
      boolean generated,
      PlanetType type,
      boolean poles) {
    public Entry(String name, String dimension, int radius, long seed, boolean generated) {
      this(name, dimension, radius, seed, generated, PlanetType.LEGACY, false);
    }
  }

  private final List<Entry> generated = new ArrayList<>();
  private static final Factory<PlanetCatalog> FACTORY =
      new Factory<>(PlanetCatalog::new, PlanetCatalog::load, null);

  public static PlanetCatalog get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "spaceblocks_planets");
  }

  private static PlanetCatalog load(CompoundTag tag, HolderLookup.Provider lookup) {
    var result = new PlanetCatalog();
    var list = tag.getList("planets", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(SLOTS, list.size()); i++) {
      var t = list.getCompound(i);
      if (!validName(t.getString("name")) || t.getInt("radius") < 32 || t.getInt("radius") > 1024)
        continue;
      result.generated.add(
          new Entry(
              t.getString("name"),
              "spaceblocks:generated_" + String.format(Locale.ROOT, "%02d", i + 1),
              t.getInt("radius"),
              t.getLong("seed"),
              true,
              t.contains("type") ? PlanetType.parse(t.getString("type")) : PlanetType.LEGACY,
              t.getBoolean("poles")));
    }
    return result;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    var list = new ListTag();
    for (var e : generated) {
      var t = new CompoundTag();
      t.putString("name", e.name());
      t.putInt("radius", e.radius());
      t.putLong("seed", e.seed());
      t.putString("type", e.type().id());
      t.putBoolean("poles", e.poles());
      list.add(t);
    }
    tag.put("planets", list);
    return tag;
  }

  public static boolean validName(String name) {
    return name.matches("[a-z][a-z0-9_-]{0,23}");
  }

  public List<Entry> entries(MinecraftServer server) {
    var result = new ArrayList<Entry>();
    for (String name : new String[] {"small", "flat", "natural", "lab"}) {
      var dimension =
          switch (name) {
            case "small" -> SpaceBlocks.SMALL;
            case "flat" -> SpaceBlocks.LARGE;
            case "lab" -> SpaceBlocks.LAB;
            default -> SpaceBlocks.NATURAL;
          };
      var level = server.getLevel(dimension);
      if (level != null && level.getChunkSource().getGenerator() instanceof PlanetGenerator g)
        result.add(
            new Entry(
                name,
                dimension.location().toString(),
                Planet.of(level).radius(),
                g.terrainSeed(level.getChunkSource().randomState()),
                false));
    }
    result.addAll(generated);
    return List.copyOf(result);
  }

  public Entry find(MinecraftServer server, String name) {
    return entries(server).stream().filter(e -> e.name().equals(name)).findFirst().orElse(null);
  }

  public static ResourceKey<Level> key(String dimension) {
    return ResourceKey.create(
        net.minecraft.core.registries.Registries.DIMENSION,
        net.minecraft.resources.ResourceLocation.parse(dimension));
  }

  private static void configure(MinecraftServer server, Entry e) {
    var level = server.getLevel(key(e.dimension()));
    if (level == null || !(level.getChunkSource().getGenerator() instanceof PlanetGenerator g))
      throw new IllegalStateException("Planet dimension unavailable: " + e.dimension());
    if (e.type() == PlanetType.LEGACY) g.configure(e.radius(), e.seed());
    else g.configureNative(server, e.radius(), e.seed(), e.type(), e.poles());
  }

  public static void started(ServerStartedEvent event) {
    var lab = event.getServer().getLevel(SpaceBlocks.LAB);
    if (lab != null
        && lab.getChunkSource().getGenerator() instanceof PlanetGenerator g
        && g.planet.radius() != Planet.LAB.radius())
      g.configure(Planet.LAB.radius(), g.terrainSeed(lab.getChunkSource().randomState()));
    var catalog = get(event.getServer());
    for (var e : catalog.generated) configure(event.getServer(), e);
  }

  public static int generate(ServerPlayer player, String name, int radius, Long seed) {
    return generate(player, name, radius, seed, PlanetType.EARTH, true);
  }

  public static int generate(
      ServerPlayer player, String name, int radius, Long seed, PlanetType type, boolean poles) {
    if (!player.hasPermissions(2)) return 0;
    var catalog = get(player.server);
    if (!validName(name)
        || radius < 32
        || radius > 128
        || type == PlanetType.LEGACY
        || type == PlanetType.NETHER && poles) {
      player.sendSystemMessage(
          Component.literal(
              "Name: lowercase letters/digits/_/-, up to 24 characters. Radius: 32..128. Nether has"
                  + " no ice poles."));
      return 0;
    }
    if (catalog.find(player.server, name) != null) {
      player.sendSystemMessage(
          Component.literal("That planet name already exists. Use /planet enter " + name));
      return 0;
    }
    if (catalog.generated.size() >= SLOTS) {
      player.sendSystemMessage(
          Component.literal(
              "This world already contains 16 generated planets. Existing planets are preserved."));
      return 0;
    }
    var e =
        create(
            player.server,
            name,
            radius,
            seed == null ? new java.security.SecureRandom().nextLong() : seed,
            type,
            poles);
    player.sendSystemMessage(
        Component.literal(
            "Created "
                + name
                + ": radius="
                + radius
                + ", map="
                + PeriodicMath.circumference(radius)
                + ", seed="
                + e.seed()
                + ", type="
                + type.id()
                + ", ice_poles="
                + poles));
    return teleport(player, name, 0, 0, null);
  }

  public static Entry create(
      MinecraftServer server, String name, int radius, long seed, PlanetType type, boolean poles) {
    var catalog = get(server);
    if (!validName(name)
        || radius < 32
        || radius > 128
        || type == PlanetType.LEGACY
        || type == PlanetType.NETHER && poles
        || catalog.find(server, name) != null
        || catalog.generated.size() >= SLOTS)
      throw new IllegalArgumentException("Invalid or existing planet");
    var entry =
        new Entry(
            name,
            "spaceblocks:generated_"
                + String.format(Locale.ROOT, "%02d", catalog.generated.size() + 1),
            radius,
            seed,
            true,
            type,
            poles);
    configure(server, entry);
    catalog.generated.add(entry);
    catalog.setDirty();
    return entry;
  }

  public static int teleport(ServerPlayer player, String name, double x, double z, Double y) {
    if (!player.hasPermissions(2)) return 0;
    var e = get(player.server).find(player.server, name);
    if (e == null) {
      player.sendSystemMessage(Component.literal("Unknown planet: " + name));
      return 0;
    }
    if (!Double.isFinite(x)
        || !Double.isFinite(z)
        || Math.abs(x) > 30_000_000
        || Math.abs(z) > 30_000_000
        || y != null && (!Double.isFinite(y) || y < -511 || y > 1022)) return 0;
    try {
      PlanetCommands.enterDimension(
          player.createCommandSourceStack().withPermission(2), key(e.dimension()));
      var planet = Planet.of(player.level());
      x = PeriodicMath.wrap(x, planet.size());
      z = PeriodicMath.wrap(z, planet.size());
      // The standard entry chooses a safe spawn. Keep it for the default surface location.
      if (y == null && x == 0 && z == 0) return 1;
      player.serverLevel().getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
      double targetY =
          y == null
              ? player
                      .serverLevel()
                      .getHeight(
                          net.minecraft.world.level.levelgen.Heightmap.Types
                              .MOTION_BLOCKING_NO_LEAVES,
                          (int) Math.floor(x),
                          (int) Math.floor(z))
                  + 1
              : y;
      player.connection.teleport(x, targetY, z, player.getYRot(), player.getXRot());
      player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
      PlanetServer.reset(player);
      return 1;
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
      return 0;
    }
  }
}
