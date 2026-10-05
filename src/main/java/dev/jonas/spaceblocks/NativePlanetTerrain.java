package dev.jonas.spaceblocks;

import com.mojang.serialization.MapCodec;
import dev.jonas.spaceblocks.mixin.PeriodicRandomStateAccessor;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

/** Vanilla noise/surface/carvers/features with a periodic density adapter and themed surfaces. */
public final class NativePlanetTerrain {
  public final PlanetType type;
  public final boolean poles;
  public final long seed;
  public final NoiseBasedChunkGenerator generator;
  public final RandomState random;
  public final ChunkGeneratorStructureState structures;
  private final Planet planet;

  private static final class Source extends BiomeSource {
    private final List<Holder<Biome>> warm;
    private final Holder<Biome> cold;
    private final PlanetType type;
    private final boolean poles;
    private final Planet planet;
    private final PeriodicTerrain climate;

    Source(Registry<Biome> registry, Planet planet, PlanetType type, boolean poles, long seed) {
      this.type = type;
      this.planet = planet;
      this.poles = poles;
      climate = new PeriodicTerrain(planet.size(), seed);
      var keys =
          type == PlanetType.EARTH
              ? List.of(
                  Biomes.OCEAN,
                  Biomes.PLAINS,
                  Biomes.FOREST,
                  Biomes.DESERT,
                  Biomes.TAIGA,
                  Biomes.SNOWY_PLAINS,
                  Biomes.STONY_PEAKS)
              : type == PlanetType.NETHER
                  ? List.of(
                      Biomes.NETHER_WASTES,
                      Biomes.CRIMSON_FOREST,
                      Biomes.WARPED_FOREST,
                      Biomes.SOUL_SAND_VALLEY,
                      Biomes.BASALT_DELTAS)
                  : List.of(type.biome);
      warm = keys.stream().map(registry::getHolderOrThrow).map(h -> (Holder<Biome>) h).toList();
      cold = registry.getHolderOrThrow(Biomes.SNOWY_PLAINS);
    }

    protected Stream<Holder<Biome>> collectPossibleBiomes() {
      return poles ? Stream.concat(warm.stream(), Stream.of(cold)) : warm.stream();
    }

    protected MapCodec<? extends BiomeSource> codec() {
      throw new UnsupportedOperationException("Runtime-only preset source");
    }

    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
      if (type.polar(z * 4, planet.size(), poles)) return cold;
      if (warm.size() == 1) return warm.getFirst();
      return warm.get(Math.floorMod(climate.biome(x * 4, z * 4), warm.size()));
    }
  }

  public NativePlanetTerrain(
      MinecraftServer server, Planet planet, long seed, PlanetType type, boolean poles) {
    this.planet = planet;
    this.seed = seed;
    this.type = type;
    this.poles = poles;
    var registry = server.registryAccess();
    var settings =
        registry
            .registryOrThrow(Registries.NOISE_SETTINGS)
            .getHolderOrThrow(
                type == PlanetType.NETHER
                    ? NoiseGeneratorSettings.NETHER
                    : NoiseGeneratorSettings.OVERWORLD);
    var source = new Source(registry.registryOrThrow(Registries.BIOME), planet, type, poles, seed);
    generator = new NoiseBasedChunkGenerator(source, settings);
    random = RandomState.create(settings.value(), registry.lookupOrThrow(Registries.NOISE), seed);
    var r = random.router();
    ((PeriodicRandomStateAccessor) (Object) random)
        .spaceblocks$router(
            new NoiseRouter(
                tile(r.barrierNoise()),
                tile(r.fluidLevelFloodednessNoise()),
                tile(r.fluidLevelSpreadNoise()),
                tile(r.lavaNoise()),
                tile(r.temperature()),
                tile(r.vegetation()),
                tile(r.continents()),
                tile(r.erosion()),
                tile(r.depth()),
                tile(r.ridges()),
                tile(r.initialDensityWithoutJaggedness()),
                DensityFunctions.interpolated(tile(r.finalDensity())),
                tile(r.veinToggle()),
                tile(r.veinRidged()),
                tile(r.veinGap())));
    structures =
        generator.createState(registry.lookupOrThrow(Registries.STRUCTURE_SET), random, seed);
  }

  private DensityFunction tile(DensityFunction source) {
    return new PeriodicDensity(source, planet.size());
  }

  public net.minecraft.world.level.block.state.BlockState flatBlock(int x, int y, int z) {
    if (y > 64 || y < -500) return Blocks.AIR.defaultBlockState();
    if (y <= -499) return Blocks.BEDROCK.defaultBlockState();
    if (y == 64)
      return (type.polar(z, planet.size(), poles) ? Blocks.SNOW_BLOCK : Blocks.GRASS_BLOCK)
          .defaultBlockState();
    return (y >= 61 ? Blocks.DIRT : Blocks.STONE).defaultBlockState();
  }

  public ChunkAccess fillFlat(ChunkAccess chunk) {
    for (var section : chunk.getSections()) section.acquire();
    try {
      var top = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
      var floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
      for (int x = 0; x < 16; x++)
        for (int z = 0; z < 16; z++)
          for (int y = -500; y <= 64; y++) {
            int wx = chunk.getPos().getMinBlockX() + x, wz = chunk.getPos().getMinBlockZ() + z;
            var state = flatBlock(wx, y, wz);
            chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
            top.update(x, y, z, state);
            floor.update(x, y, z, state);
          }
      return chunk;
    } finally {
      for (var section : chunk.getSections()) section.release();
    }
  }

  public void foundation(ChunkAccess chunk) {
    var rock =
        (type == PlanetType.NETHER ? Blocks.NETHERRACK : Blocks.DEEPSLATE).defaultBlockState();
    var position = new BlockPos.MutableBlockPos();
    int top = generator.getMinY();
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        int wx = chunk.getPos().getMinBlockX() + x, wz = chunk.getPos().getMinBlockZ() + z;
        for (int y = -500; y < top; y++)
          chunk.setBlockState(
              position.set(wx, y, wz),
              y <= -499 ? Blocks.BEDROCK.defaultBlockState() : rock,
              false);
      }
  }

  public void finishSurface(ChunkAccess chunk) {
    var position = new BlockPos.MutableBlockPos();
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        int wx = chunk.getPos().getMinBlockX() + x, wz = chunk.getPos().getMinBlockZ() + z;
        // Preserve deep traversal: vanilla's old bottom bedrock is rock inside this thicker planet.
        for (int y = generator.getMinY(); y < generator.getMinY() + 6; y++) {
          position.set(wx, y, wz);
          if (chunk.getBlockState(position).is(Blocks.BEDROCK))
            chunk.setBlockState(
                position,
                (type == PlanetType.NETHER ? Blocks.NETHERRACK : Blocks.DEEPSLATE)
                    .defaultBlockState(),
                false);
        }
        if (type == PlanetType.NETHER) {
          // Open the native Nether's ceiling for a planet surface; no atmospheric lighting changes.
          for (int y = 96; y < 128; y++)
            chunk.setBlockState(position.set(wx, y, wz), Blocks.AIR.defaultBlockState(), false);
        }
        if (type != PlanetType.DIRT && type != PlanetType.STONE && type != PlanetType.DESERT)
          continue;
        if (type == PlanetType.DESERT || type == PlanetType.DIRT || type == PlanetType.STONE) {
          // These are dry surface presets, not an ocean with a different biome label.
          int oceanFloor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
          for (int y = oceanFloor + 1; y <= 64; y++) {
            position.set(wx, y, wz);
            if (chunk.getBlockState(position).is(Blocks.WATER))
              chunk.setBlockState(
                  position,
                  (type == PlanetType.DESERT
                          ? Blocks.SANDSTONE
                          : type == PlanetType.DIRT ? Blocks.DIRT : Blocks.STONE)
                      .defaultBlockState(),
                  false);
          }
        }
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        for (int y = top; y >= Math.max(generator.getMinY(), top - 8); y--) {
          position.set(wx, y, wz);
          var state = chunk.getBlockState(position);
          if (!state.isAir())
            chunk.setBlockState(
                position,
                (type == PlanetType.DIRT
                        ? Blocks.DIRT
                        : type == PlanetType.DESERT ? Blocks.SAND : Blocks.STONE)
                    .defaultBlockState(),
                false);
        }
      }
  }

  public String resources() {
    return switch (type) {
      case DESERT -> "gold,copper";
      case JUNGLE -> "copper,iron";
      case MUSHROOM -> "coal,iron";
      case DIRT -> "coal,copper";
      case STONE -> "iron,redstone";
      case NETHER -> "quartz,gold";
      case FLAT -> "none";
      default -> "diverse";
    };
  }

  public void enrich(WorldGenLevel level, ChunkAccess chunk) {
    if (type == PlanetType.EARTH || type == PlanetType.FLAT) return;
    var rng =
        net.minecraft.util.RandomSource.create(
            PeriodicTerrain.hash(seed, chunk.getPos().x, 733, chunk.getPos().z));
    var choices =
        switch (type) {
          case DESERT -> new Block[] {Blocks.GOLD_ORE, Blocks.COPPER_ORE};
          case JUNGLE -> new Block[] {Blocks.COPPER_ORE, Blocks.IRON_ORE};
          case DIRT -> new Block[] {Blocks.COAL_ORE, Blocks.COPPER_ORE};
          case MUSHROOM -> new Block[] {Blocks.COAL_ORE, Blocks.IRON_ORE};
          case STONE -> new Block[] {Blocks.IRON_ORE, Blocks.REDSTONE_ORE};
          case NETHER -> new Block[] {Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE};
          default ->
              new Block[] {
                Blocks.IRON_ORE,
                Blocks.COAL_ORE,
                Blocks.COPPER_ORE,
                Blocks.GOLD_ORE,
                Blocks.REDSTONE_ORE,
                Blocks.LAPIS_ORE,
                Blocks.DIAMOND_ORE
              };
        };
    // Use Minecraft's vein feature, not independent ore blocks or fixed regular grids.
    for (int i = 0; i < 6; i++) {
      int y = type == PlanetType.NETHER ? rng.nextInt(80) + 8 : rng.nextInt(80);
      int x = chunk.getPos().getMinBlockX() + rng.nextInt(16),
          z = chunk.getPos().getMinBlockZ() + rng.nextInt(16);
      net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest rule =
          type == PlanetType.NETHER
              ? new net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest(
                  Blocks.NETHERRACK)
              : new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
      Feature.ORE.place(
          new OreConfiguration(rule, choices[rng.nextInt(choices.length)].defaultBlockState(), 12),
          level,
          generator,
          rng,
          new BlockPos(x, y, z));
    }
  }
}
