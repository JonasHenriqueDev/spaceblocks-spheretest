package dev.jonas.spaceblocks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class PlanetChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(PlanetChunkGenerator::getBiomeSource),
            PlanetDefinition.CODEC.fieldOf("planet").forGetter(PlanetChunkGenerator::planet)
    ).apply(instance, PlanetChunkGenerator::new));

    private final PlanetDefinition planet;

    public PlanetChunkGenerator(BiomeSource biomes, PlanetDefinition planet) {
        super(biomes);
        this.planet = planet;
    }

    public PlanetDefinition planet() { return planet; }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return planet.radius() > 128 ? 768 : 384; }
    @Override public int getMinY() { return planet.radius() > 128 ? -256 : -64; }
    @Override public int getSeaLevel() { return getMinY(); }
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random,
                                      BiomeManager biomes, StructureManager structures, ChunkAccess chunk,
                                      GenerationStep.Carving step) {}
    @Override public void buildSurface(WorldGenRegion region, StructureManager structures,
                                       RandomState random, ChunkAccess chunk) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}

    public BlockState blockAt(int x, int y, int z) {
        double distance = planet.distance(x + 0.5, y + 0.5, z + 0.5);
        if (distance < planet.radius() - 9) return Blocks.STONE.defaultBlockState();
        if (distance > planet.radius() + 5) return Blocks.AIR.defaultBlockState();
        double depth = planet.surfaceRadius(x + 0.5, y + 0.5, z + 0.5) - distance;
        if (depth < 0) return Blocks.AIR.defaultBlockState();
        if (depth < 1.6) return Blocks.MOSS_BLOCK.defaultBlockState();
        if (depth < 4.0) return Blocks.DIRT.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        double extent = planet.radius() + 5.0;
        if (minX > planet.centerX() + extent || minX + 16 < planet.centerX() - extent
                || minZ > planet.centerZ() + extent || minZ + 16 < planet.centerZ() - extent) {
            return CompletableFuture.completedFuture(chunk);
        }
        int minY = Math.max(chunk.getMinBuildHeight(), (int) Math.floor(planet.centerY() - extent));
        int maxY = Math.min(chunk.getMaxBuildHeight() - 1, (int) Math.ceil(planet.centerY() + extent));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = blockAt(minX + x, y, minZ + z);
                    if (state.isAir()) continue;
                    chunk.setBlockState(pos.set(minX + x, y, minZ + z), state, false);
                    floor.update(x, y, z, state);
                    surface.update(x, y, z, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int top = Math.min(level.getMaxBuildHeight() - 1, (int) Math.ceil(planet.centerY() + planet.radius() + 5));
        for (int y = top; y >= level.getMinBuildHeight(); y--) {
            if (type.isOpaque().test(blockAt(x, y, z))) return y + 1;
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) states[i] = blockAt(x, level.getMinBuildHeight() + i, z);
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos pos) {
        lines.add("Space Blocks | body seed " + planet.seed());
        lines.add(String.format(java.util.Locale.ROOT, "Radial altitude: %.1f blocks", -planet.depth(pos.getX(), pos.getY(), pos.getZ())));
    }
}
