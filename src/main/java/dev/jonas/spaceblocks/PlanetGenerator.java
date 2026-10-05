package dev.jonas.spaceblocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class PlanetGenerator extends ChunkGenerator {
  public static final MapCodec<PlanetGenerator> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      BiomeSource.CODEC
                          .fieldOf("biome_source")
                          .forGetter(PlanetGenerator::getBiomeSource),
                      Codec.intRange(32, 1024).fieldOf("radius").forGetter(g -> g.planet.radius()),
                      Codec.BOOL.optionalFieldOf("natural", false).forGetter(g -> g.natural),
                      Codec.LONG.optionalFieldOf("terrain_seed").forGetter(g -> g.explicitSeed))
                  .apply(i, PlanetGenerator::new));
  public Planet planet;
  private java.util.Optional<Long> explicitSeed = java.util.Optional.empty();
  public final boolean natural;
  public volatile NativePlanetTerrain nativeTerrain;
  private volatile PeriodicTerrain terrain;

  public PlanetGenerator(BiomeSource source, int radius) {
    this(source, radius, false);
  }

  public PlanetGenerator(BiomeSource source, int radius, boolean natural) {
    this(source, radius, natural, java.util.Optional.empty());
  }

  public PlanetGenerator(
      BiomeSource source, int radius, boolean natural, java.util.Optional<Long> seed) {
    super(source);
    planet = new Planet(radius);
    this.natural = natural;
    explicitSeed = seed;
    if (seed.isPresent() && source instanceof PeriodicBiomeSource periodic)
      periodic.configure(radius, seed.get());
  }

  public void configure(int radius, long seed) {
    planet = new Planet(radius);
    explicitSeed = java.util.Optional.of(seed);
    terrain = null;
    nativeTerrain = null;
    if (getBiomeSource() instanceof PeriodicBiomeSource periodic) periodic.configure(radius, seed);
  }

  public void configureNative(
      net.minecraft.server.MinecraftServer server,
      int radius,
      long seed,
      PlanetType type,
      boolean poles) {
    configure(radius, seed);
    nativeTerrain = new NativePlanetTerrain(server, planet, seed, type, poles);
  }

  @Override
  public CompletableFuture<ChunkAccess> createBiomes(
      RandomState random, Blender blender, StructureManager structures, ChunkAccess chunk) {
    var nativeGen = nativeTerrain;
    return nativeGen == null
        ? super.createBiomes(random, blender, structures, chunk)
        : nativeGen.generator.createBiomes(nativeGen.random, blender, structures, chunk);
  }

  public long terrainSeed(RandomState random) {
    return explicitSeed.orElseGet(() -> PeriodicTerrain.seed(random.sampler()));
  }

  private PeriodicTerrain terrain(RandomState random) {
    var result = terrain;
    if (result == null) {
      long seed = terrainSeed(random);
      terrain = result = new PeriodicTerrain(planet.size(), seed);
    }
    return result;
  }

  public int surface(int x, int z, RandomState random) {
    if (nativeTerrain != null && nativeTerrain.type == PlanetType.FLAT) return Planet.SURFACE;
    if (nativeTerrain != null)
      return nativeTerrain.generator.getBaseHeight(
              x,
              z,
              Heightmap.Types.WORLD_SURFACE_WG,
              net.minecraft.world.level.LevelHeightAccessor.create(-512, 1536),
              nativeTerrain.random)
          - 1;
    return natural ? terrain(random).surface(x, z) : Planet.SURFACE;
  }

  private record Tree(int dx, int dz, int kind, int crown) {}

  private List<Tree> trees(int x, int z, PeriodicTerrain noise) {
    var result = new java.util.ArrayList<Tree>(1);
    for (int gx = -1; gx <= 1; gx++)
      for (int gz = -1; gz <= 1; gz++) {
        int tx = PeriodicMath.wrap(Math.floorDiv(x, 16) * 16 + 8 + gx * 16, planet.size());
        int tz = PeriodicMath.wrap(Math.floorDiv(z, 16) * 16 + 8 + gz * 16, planet.size());
        int dx = Math.abs(PeriodicMath.wrap(x - tx, planet.size()));
        int dz = Math.abs(PeriodicMath.wrap(z - tz, planet.size()));
        if (dx > 2 || dz > 2) continue;
        long hash = PeriodicTerrain.hash(0, tx, 0, tz);
        int kind = noise.biome(tx, tz), base = noise.surface(tx, tz);
        if ((kind == 2 || kind == 4) && (hash & 3) != 0 && base >= 66)
          result.add(new Tree(dx, dz, kind, base + 5));
      }
    return result;
  }

  private BlockState naturalBlock(
      int x,
      int y,
      int z,
      int height,
      PeriodicTerrain.CaveColumn caves,
      int biome,
      List<Tree> trees) {
    if (y <= -496) return (y >= -500 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState();
    if (y > height) {
      if (y <= 64) return (biome == 5 && y == 64 ? Blocks.ICE : Blocks.WATER).defaultBlockState();
      for (var tree : trees) {
        if (tree.dx == 0 && tree.dz == 0 && y <= tree.crown)
          return (tree.kind == 4 ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG).defaultBlockState();
        if (y >= tree.crown - 2
            && y <= tree.crown + 1
            && tree.dx + tree.dz + Math.abs(y - tree.crown) <= 5)
          return (tree.kind == 4 ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES).defaultBlockState();
      }
      if (y == height + 1 && height > 65 && biome == 5) return Blocks.SNOW.defaultBlockState();
      if (y == height + 1
          && height > 65
          && (biome == 1 || biome == 2)
          && (PeriodicTerrain.hash(9, x, 0, z) & 31) == 0)
        return ((PeriodicTerrain.hash(10, x, 0, z) & 7) == 0 ? Blocks.POPPY : Blocks.SHORT_GRASS)
            .defaultBlockState();
      return Blocks.AIR.defaultBlockState();
    }
    if (caves.cave(y)) return (y < -450 ? Blocks.LAVA : Blocks.AIR).defaultBlockState();
    if (y == height)
      return (height <= 65 || biome == 3
              ? Blocks.SAND
              : biome == 6 ? Blocks.STONE : Blocks.GRASS_BLOCK)
          .defaultBlockState();
    if (y >= height - 3)
      return (height <= 65 || biome == 3 ? Blocks.SAND : Blocks.DIRT).defaultBlockState();
    boolean deep = y < 0;
    long h =
        PeriodicTerrain.hash(
            77,
            Math.floorDiv(PeriodicMath.wrap(x, planet.size()), 3),
            Math.floorDiv(y, 3),
            Math.floorDiv(PeriodicMath.wrap(z, planet.size()), 3));
    int ore = (int) (h & 511);
    var rock = deep ? Blocks.DEEPSLATE : Blocks.STONE;
    if (ore < 10 && y < 120) rock = deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
    else if (ore < 19 && y < 90) rock = deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
    else if (ore < 25 && y < 60) rock = deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
    else if (ore < 28 && y < 0) rock = Blocks.DEEPSLATE_GOLD_ORE;
    else if (ore < 33 && y < 0) rock = Blocks.DEEPSLATE_REDSTONE_ORE;
    else if (ore < 35 && y < -30) rock = Blocks.DEEPSLATE_LAPIS_ORE;
    else if (ore < 37 && y < -60) rock = Blocks.DEEPSLATE_DIAMOND_ORE;
    return rock.defaultBlockState();
  }

  @Override
  protected MapCodec<? extends ChunkGenerator> codec() {
    return CODEC;
  }

  @Override
  public int getGenDepth() {
    return 1536;
  }

  @Override
  public int getMinY() {
    return -512;
  }

  @Override
  public int getSeaLevel() {
    return Planet.SURFACE;
  }

  @Override
  public void applyCarvers(
      WorldGenRegion r,
      long s,
      RandomState n,
      BiomeManager b,
      StructureManager m,
      ChunkAccess c,
      GenerationStep.Carving step) {
    if (nativeTerrain != null
        && nativeTerrain.type != PlanetType.FLAT
        && planet.contains(c.getPos()))
      nativeTerrain.generator.applyCarvers(
          r, nativeTerrain.seed, nativeTerrain.random, b, m, c, step);
  }

  @Override
  public void buildSurface(WorldGenRegion r, StructureManager m, RandomState n, ChunkAccess c) {
    if (nativeTerrain != null
        && nativeTerrain.type != PlanetType.FLAT
        && planet.contains(c.getPos())) {
      nativeTerrain.generator.buildSurface(r, m, nativeTerrain.random, c);
      nativeTerrain.finishSurface(c);
    }
  }

  @Override
  public void spawnOriginalMobs(WorldGenRegion r) {
    if (!natural || !planet.contains(r.getCenter())) return;
    if (nativeTerrain != null) {
      nativeTerrain.generator.spawnOriginalMobs(r);
      return;
    }
    var cp = r.getCenter();
    var random = new WorldgenRandom(new LegacyRandomSource(r.getSeed()));
    random.setDecorationSeed(r.getSeed(), cp.getMinBlockX(), cp.getMinBlockZ());
    NaturalSpawner.spawnMobsForChunkGeneration(
        r, r.getBiome(cp.getWorldPosition().atY(64)), cp, random);
  }

  @Override
  public void applyBiomeDecoration(
      WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
    if (nativeTerrain != null
        && nativeTerrain.type != PlanetType.FLAT
        && planet.contains(chunk.getPos())) {
      nativeTerrain.generator.applyBiomeDecoration(level, chunk, structures);
      nativeTerrain.enrich(level, chunk);
    }
  }

  @Override
  public void createStructures(
      net.minecraft.core.RegistryAccess registry,
      ChunkGeneratorStructureState state,
      StructureManager manager,
      ChunkAccess chunk,
      net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
          templates) {
    if (nativeTerrain != null
        && nativeTerrain.type != PlanetType.FLAT
        && planet.contains(chunk.getPos()))
      nativeTerrain.generator.createStructures(
          registry, nativeTerrain.structures, manager, chunk, templates);
  }

  @Override
  public void createReferences(WorldGenLevel level, StructureManager manager, ChunkAccess chunk) {
    if (nativeTerrain != null
        && nativeTerrain.type != PlanetType.FLAT
        && planet.contains(chunk.getPos()))
      nativeTerrain.generator.createReferences(level, manager, chunk);
  }

  public BlockState block(int y) {
    if (y > Planet.SURFACE || y <= planet.bottom()) return Blocks.AIR.defaultBlockState();
    return (y == Planet.SURFACE
            ? Blocks.GRASS_BLOCK
            : y >= Planet.SURFACE - 3 ? Blocks.DIRT : Blocks.STONE)
        .defaultBlockState();
  }

  @Override
  public CompletableFuture<ChunkAccess> fillFromNoise(
      Blender b, RandomState r, StructureManager s, ChunkAccess c) {
    if (!planet.contains(c.getPos())) return CompletableFuture.completedFuture(c);
    if (nativeTerrain != null) {
      var nativeGen = nativeTerrain;
      if (nativeGen.type == PlanetType.FLAT)
        return CompletableFuture.supplyAsync(
            () -> nativeGen.fillFlat(c), net.minecraft.Util.backgroundExecutor());
      return nativeGen
          .generator
          .fillFromNoise(b, nativeGen.random, s, c)
          .thenApply(
              chunk -> {
                nativeGen.foundation(chunk);
                return chunk;
              });
    }
    return CompletableFuture.supplyAsync(
        net.minecraft.Util.wrapThreadWithTaskName(
            "periodic_terrain",
            () -> {
              for (var section : c.getSections()) section.acquire();
              try {
                return fill(r, c);
              } finally {
                for (var section : c.getSections()) section.release();
              }
            }),
        net.minecraft.Util.backgroundExecutor());
  }

  private ChunkAccess fill(RandomState r, ChunkAccess c) {
    var noise = natural ? terrain(r) : null;
    var floor = c.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
    var top = c.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        int wx = c.getPos().getMinBlockX() + x, wz = c.getPos().getMinBlockZ() + z;
        int height = natural ? noise.surface(wx, wz) : Planet.SURFACE,
            biome = natural ? noise.biome(wx, wz) : 1;
        var caves = natural ? noise.caves(wx, wz, height) : null;
        var trees = natural ? trees(wx, wz, noise) : List.<Tree>of();
        var structures =
            natural
                ? PeriodicStructures.columns(wx, wz, planet.size(), noise)
                : List.<PeriodicStructures.Column>of();
        int ceiling = natural ? Math.max(64, height) + 7 : Planet.SURFACE;
        for (var structure : structures) ceiling = Math.max(ceiling, structure.floor() + 5);
        if (natural)
          for (int dx = -16; dx <= 16; dx += 16)
            for (int dz = -16; dz <= 16; dz += 16)
              ceiling =
                  Math.max(
                      ceiling,
                      noise.surface(
                              Math.floorDiv(wx, 16) * 16 + 8 + dx,
                              Math.floorDiv(wz, 16) * 16 + 8 + dz)
                          + 7);
        for (int y = natural ? -500 : planet.bottom() + 1; y <= ceiling; y++) {
          var state = natural ? naturalBlock(wx, y, wz, height, caves, biome, trees) : block(y);
          long lootSeed = 0;
          for (var structure : structures) {
            var placed = structure.block(y);
            if (placed != null) {
              state = placed;
              lootSeed = structure.lootSeed();
            }
          }
          if (state.isAir()) continue;
          c.getSection(c.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
          if (natural && (state.is(Blocks.CHEST) || state.is(Blocks.SPAWNER))) {
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putString(
                "id", state.is(Blocks.CHEST) ? "minecraft:chest" : "minecraft:mob_spawner");
            tag.putInt("x", wx);
            tag.putInt("y", y);
            tag.putInt("z", wz);
            if (state.is(Blocks.CHEST)) {
              tag.putString("LootTable", "minecraft:chests/simple_dungeon");
              tag.putLong("LootTableSeed", lootSeed);
            } else {
              var data = new net.minecraft.nbt.CompoundTag();
              var entity = new net.minecraft.nbt.CompoundTag();
              entity.putString("id", "minecraft:zombie");
              data.put("entity", entity);
              tag.put("SpawnData", data);
            }
            c.setBlockEntityNbt(tag);
          }
          floor.update(x, y, z, state);
          top.update(x, y, z, state);
        }
      }
    return c;
  }

  @Override
  public int getBaseHeight(int x, int z, Heightmap.Types t, LevelHeightAccessor l, RandomState r) {
    if (nativeTerrain != null && nativeTerrain.type == PlanetType.FLAT) return Planet.SURFACE + 1;
    if (nativeTerrain != null)
      return nativeTerrain.generator.getBaseHeight(x, z, t, l, nativeTerrain.random);
    return Math.max(
            surface(x, z, r), t == Heightmap.Types.WORLD_SURFACE_WG ? 64 : Integer.MIN_VALUE)
        + 1;
  }

  @Override
  public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor l, RandomState r) {
    if (nativeTerrain != null && nativeTerrain.type == PlanetType.FLAT) {
      var states = new BlockState[l.getHeight()];
      for (int i = 0; i < states.length; i++)
        states[i] = nativeTerrain.flatBlock(x, l.getMinBuildHeight() + i, z);
      return new NoiseColumn(l.getMinBuildHeight(), states);
    }
    if (nativeTerrain != null)
      return nativeTerrain.generator.getBaseColumn(x, z, l, nativeTerrain.random);
    var states = new BlockState[l.getHeight()];
    int height = surface(x, z, r), biome = natural ? terrain(r).biome(x, z) : 1;
    var caves = natural ? terrain(r).caves(x, z, height) : null;
    var trees = natural ? trees(x, z, terrain(r)) : List.<Tree>of();
    for (int i = 0; i < states.length; i++)
      states[i] =
          natural
              ? naturalBlock(x, l.getMinBuildHeight() + i, z, height, caves, biome, trees)
              : block(l.getMinBuildHeight() + i);
    return new NoiseColumn(l.getMinBuildHeight(), states);
  }

  @Override
  public void addDebugScreenInfo(List<String> lines, RandomState r, BlockPos p) {
    lines.add(
        "Periodic map: " + planet.size() + " x " + planet.size() + ", radius " + planet.radius());
  }
}
