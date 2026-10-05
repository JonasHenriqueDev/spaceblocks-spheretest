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
                      Codec.intRange(32, 256).fieldOf("radius").forGetter(g -> g.planet.radius()))
                  .apply(i, PlanetGenerator::new));
  public final Planet planet;

  public PlanetGenerator(BiomeSource source, int radius) {
    super(source);
    planet = new Planet(radius);
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
      GenerationStep.Carving step) {}

  @Override
  public void buildSurface(WorldGenRegion r, StructureManager m, RandomState n, ChunkAccess c) {}

  @Override
  public void spawnOriginalMobs(WorldGenRegion r) {}

  @Override
  public void applyBiomeDecoration(
      WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}

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
    var pos = new BlockPos.MutableBlockPos();
    var floor = c.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
    var top = c.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = planet.bottom() + 1; y <= Planet.SURFACE; y++) {
          var state = block(y);
          c.setBlockState(
              pos.set(c.getPos().getMinBlockX() + x, y, c.getPos().getMinBlockZ() + z),
              state,
              false);
          floor.update(x, y, z, state);
          top.update(x, y, z, state);
        }
    return CompletableFuture.completedFuture(c);
  }

  @Override
  public int getBaseHeight(int x, int z, Heightmap.Types t, LevelHeightAccessor l, RandomState r) {
    return Planet.SURFACE + 1;
  }

  @Override
  public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor l, RandomState r) {
    var states = new BlockState[l.getHeight()];
    for (int i = 0; i < states.length; i++) states[i] = block(l.getMinBuildHeight() + i);
    return new NoiseColumn(l.getMinBuildHeight(), states);
  }

  @Override
  public void addDebugScreenInfo(List<String> lines, RandomState r, BlockPos p) {
    lines.add(
        "Periodic map: " + planet.size() + " x " + planet.size() + ", radius " + planet.radius());
  }
}
