package dev.jonas.spaceblocks;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Packaged dedicated-server generation checks, only under the explicit developer test flag. */
public final class PlanetPresetTests {
  private static boolean begun, done;
  private static int index;
  private static int caveAir, ores, trees;
  private static long start, deadline;
  private static CompletableFuture<?> pending;
  private static List<PlanetCatalog.Entry> entries;
  private static final List<String> results = new ArrayList<>();
  private static final List<PlanetType> types =
      Arrays.stream(PlanetType.values()).filter(t -> t != PlanetType.LEGACY).toList();

  private static void check(boolean condition, String label) {
    if (!condition) throw new IllegalStateException(label);
    results.add("PASS " + label);
    SpaceBlocks.LOGGER.info("PRESET_TEST {}", label);
  }

  public static void tick(ServerTickEvent.Post event) {
    if (!Boolean.getBoolean("spaceblocks.testPresets") || done) return;
    var server = event.getServer();
    try {
      if (!begun) {
        begun = true;
        check(
            SpaceBlocks.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString()
                .contains("spaceblocks-0.9.0.jar"),
            "Packaged 0.9.0 JAR loaded on dedicated server");
        boolean reopened = Boolean.getBoolean("spaceblocks.presetsPersistence");
        entries = new ArrayList<>();
        for (var type : types) {
          var name = "typed_" + type.id();
          if (!reopened)
            entries.add(
                PlanetCatalog.create(
                    server,
                    name,
                    type == PlanetType.FLAT ? 128 : 32,
                    421337 + type.ordinal(),
                    type,
                    type.defaultPoles));
          else {
            var entry = PlanetCatalog.get(server).find(server, name);
            check(
                entry != null && entry.type() == type && entry.poles() == type.defaultPoles,
                "Reopened catalog retains " + name + " type and poles");
            entries.add(entry);
          }
        }
        check(
            PlanetCatalog.get(server).entries(server).size() == 12,
            "Eight themed planets plus four built-in planets");
        try {
          PlanetCatalog.create(server, "too_large", 129, 42, PlanetType.EARTH, true);
          throw new IllegalStateException("Size cap ignored");
        } catch (IllegalArgumentException expected) {
          check(true, "New planets reject requested radius above 128");
        }
      }
      if (index == entries.size()) {
        check(
            caveAir > 0, "Generated chunks contain underground cave air (" + caveAir + " samples)");
        check(ores > 0, "Generated chunks contain native ore blocks (" + ores + " samples)");
        SpaceBlocks.LOGGER.info("PRESET_TEST sampled native logs={}", trees);
        // Normal shutdown flushes the world outside this tick callback and stops the watchdog.
        var path =
            server
                .getServerDirectory()
                .resolve(
                    Boolean.getBoolean("spaceblocks.presetsPersistence")
                        ? "preset-reopen-results.txt"
                        : "preset-results.txt");
        Files.write(path, results);
        SpaceBlocks.LOGGER.info("PRESET_TEST_PASS checks={}", results.size());
        done = true;
        server.halt(false);
        return;
      }
      var entry = entries.get(index);
      var level = server.getLevel(PlanetCatalog.key(entry.dimension()));
      var g = (PlanetGenerator) level.getChunkSource().getGenerator();
      if (pending == null) {
        start = System.nanoTime();
        deadline = start + 180_000_000_000L;
        int edge = g.planet.size() / 32 - 1;
        var centre = level.getChunkSource().getChunkFuture(0, 0, ChunkStatus.FULL, true);
        var pole = level.getChunkSource().getChunkFuture(0, edge, ChunkStatus.FULL, true);
        var seam = level.getChunkSource().getChunkFuture(-edge - 1, 0, ChunkStatus.FULL, true);
        pending = CompletableFuture.allOf(centre, pole, seam);
        return;
      }
      if (!pending.isDone()) {
        if (System.nanoTime() > deadline)
          throw new IllegalStateException("Generation timeout for " + entry.name());
        return;
      }
      pending.join();
      var centre = level.getChunkSource().getChunk(0, 0, ChunkStatus.FULL, false);
      var polar =
          level.getChunkSource().getChunk(0, g.planet.size() / 32 - 1, ChunkStatus.FULL, false);
      check(
          centre != null && polar != null,
          "Full chunks generated for "
              + entry.type().id()
              + " in "
              + String.format(Locale.ROOT, "%.2f", (System.nanoTime() - start) / 1e9)
              + " seconds");
      check(
          g.nativeTerrain != null && g.nativeTerrain.type == entry.type(),
          "Native preset selected for " + entry.type().id());
      if (entry.type() != PlanetType.FLAT) {
        var position = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++)
          for (int z = 0; z < 16; z++) {
            int surface = centre.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            for (int y = g.nativeTerrain.generator.getMinY() + 8; y < 128; y++) {
              var state = centre.getBlockState(position.set(x, y, z));
              if (state.isAir() && y < surface - 5) caveAir++;
              if (net.minecraft.core.registries.BuiltInRegistries.BLOCK
                  .getKey(state.getBlock())
                  .getPath()
                  .endsWith("_ore")) ores++;
              if (state.is(net.minecraft.tags.BlockTags.LOGS)) trees++;
            }
          }
      }
      if (entry.type() == PlanetType.EARTH) {
        var other =
            new NativePlanetTerrain(
                server, g.planet, entry.seed() + 1, entry.type(), entry.poles());
        boolean different = false;
        for (int x = -80; x <= 80; x += 16)
          for (int z = -80; z <= 80; z += 16) {
            var sample = new DensityFunction.SinglePointContext(x, 60, z);
            different |=
                g.nativeTerrain.random.router().finalDensity().compute(sample)
                    != other.random.router().finalDensity().compute(sample);
          }
        check(different, "Different seed changes native periodic density");
      }
      check(
          centre.getBlockState(new BlockPos(1, -500, 1)).is(Blocks.BEDROCK),
          "Deep foundation retained for " + entry.type().id());
      check(
          !centre.getBlockState(new BlockPos(1, g.planet.bottom() - 1, 1)).is(Blocks.BEDROCK)
              && !centre.getBlockState(new BlockPos(1, g.planet.bottom(), 1)).is(Blocks.BEDROCK),
          "Bottom passage and approach remain mineable for " + entry.type().id());
      var density = g.nativeTerrain.random.router().finalDensity();
      int half = g.planet.size() / 2;
      double a = density.compute(new DensityFunction.SinglePointContext(-half, 60, 8));
      double b = density.compute(new DensityFunction.SinglePointContext(half, 60, 8));
      check(Double.isFinite(a) && a == b, "Density closes X seam for " + entry.type().id());
      a = density.compute(new DensityFunction.SinglePointContext(8, 60, -half));
      b = density.compute(new DensityFunction.SinglePointContext(8, 60, half));
      check(Double.isFinite(a) && a == b, "Density closes Z seam for " + entry.type().id());
      if (entry.poles())
        check(
            polar.getNoiseBiome(0, 16, (g.planet.size() / 32 - 1) * 4).is(Biomes.SNOWY_PLAINS),
            "Polar biome is snowy for " + entry.type().id());
      else if (entry.type() != PlanetType.EARTH && entry.type() != PlanetType.NETHER)
        check(
            centre.getNoiseBiome(0, 16, 0).is(entry.type().biome),
            "Requested native biome for " + entry.type().id());
      if (entry.type() == PlanetType.FLAT) {
        boolean flat = true;
        for (int x = 0; x < 16; x++)
          for (int z = 0; z < 16; z++)
            flat &=
                centre.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
                    == (Boolean.getBoolean("spaceblocks.presetsPersistence") && x == 2 && z == 2
                        ? 200
                        : 64);
        check(
            flat, "Flat surface is constant across every column, preserving the saved test marker");
      }
      if (Boolean.getBoolean("spaceblocks.presetsPersistence"))
        check(
            level.getBlockState(new BlockPos(2, 200, 2)).is(Blocks.DIAMOND_BLOCK),
            "Reopened construction retained for " + entry.type().id());
      else level.setBlock(new BlockPos(2, 200, 2), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
      index++;
      pending = null;
    } catch (Throwable failure) {
      done = true;
      SpaceBlocks.LOGGER.error("PRESET_TEST_FAIL", failure);
      try {
        Files.writeString(
            server.getServerDirectory().resolve("preset-results.txt"), "FAIL " + failure);
      } catch (Exception ignored) {
      }
      server.halt(false);
    }
  }
}
