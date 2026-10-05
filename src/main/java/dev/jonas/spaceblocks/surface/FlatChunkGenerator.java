package dev.jonas.spaceblocks.surface;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class FlatChunkGenerator extends ChunkGenerator {
    public static final MapCodec<FlatChunkGenerator> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(FlatChunkGenerator::getBiomeSource),
        FlatDefinition.CODEC.fieldOf("surface").forGetter(FlatChunkGenerator::surface)).apply(i,FlatChunkGenerator::new));
    private final FlatDefinition surface;
    public volatile FlatEdits edits;
    public FlatChunkGenerator(BiomeSource source,FlatDefinition surface) { super(source);this.surface=surface; }
    public FlatDefinition surface() { return surface; }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return 1024; }
    @Override public int getMinY() { return -64; }
    @Override public int getSeaLevel() { return -64; }
    @Override public void applyCarvers(WorldGenRegion r,long s,RandomState n,BiomeManager b,StructureManager m,ChunkAccess c,GenerationStep.Carving step) {}
    @Override public void buildSurface(WorldGenRegion r,StructureManager m,RandomState n,ChunkAccess c) {}
    @Override public void spawnOriginalMobs(WorldGenRegion r) {}
    public BlockState blockAt(int x,int y,int z) {
        var p=surface.locate(x+.5,z+.5);
        if(p==null) return Blocks.AIR.defaultBlockState();
        if(edits!=null) {
            BlockState changed=edits.get(surface.address(p,y));
            if(changed!=null) {
                // Inventories remain at their authoritative core address until shared block entities are implemented.
                if(changed.hasBlockEntity() && !CubeTopology.canonical(p,surface.faceSize()).equals(p)) return Blocks.AIR.defaultBlockState();
                return changed.rotate(Rotation.values()[Math.floorMod(-surface.quarterTurns(p),4)]);
            }
        }
        int top=surface.height(p);
        if(y>top || y<top-24) return Blocks.AIR.defaultBlockState();
        if(y==top) return surface.coloredFaces()?FlatDefinition.faceBlock(CubeTopology.canonical(p,surface.faceSize()).face()):Blocks.MOSS_BLOCK.defaultBlockState();
        if(y>=top-3) return Blocks.DIRT.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender b,RandomState r,StructureManager s,ChunkAccess c) {
        int x0=c.getPos().getMinBlockX(),z0=c.getPos().getMinBlockZ();
        var pos=new BlockPos.MutableBlockPos();
        Heightmap floor=c.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG),top=c.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        // Include saved edits above the natural terrain; normal chunks retain the full build height.
        int maxY=edits==null?(surface.relief()?112:70):Math.min(959,Math.max(surface.relief()?112:70,edits.highestY()));
        int minY=edits==null?(surface.relief()?4:36):Math.max(-64,Math.min(surface.relief()?4:36,edits.lowestY()));
        for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
            if(surface.locate(x0+x+.5,z0+z+.5)==null) continue;
            for(int y=minY;y<=maxY;y++) {
                BlockState state=blockAt(x0+x,y,z0+z);
                if(state.isAir()) continue;
                c.setBlockState(pos.set(x0+x,y,z0+z),state,false);floor.update(x,y,z,state);top.update(x,y,z,state);
            }
        }
        return CompletableFuture.completedFuture(c);
    }
    @Override public int getBaseHeight(int x,int z,Heightmap.Types t,LevelHeightAccessor l,RandomState r) {
        var p=surface.locate(x+.5,z+.5);return p==null?l.getMinBuildHeight():surface.height(p)+1;
    }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor l,RandomState r) {
        var states=new BlockState[l.getHeight()];for(int i=0;i<states.length;i++) states[i]=blockAt(x,l.getMinBuildHeight()+i,z);
        return new NoiseColumn(l.getMinBuildHeight(),states);
    }
    @Override public void addDebugScreenInfo(List<String> lines,RandomState r,BlockPos p) {
        var chart=surface.locate(p.getX(),p.getZ());
        lines.add("Space Blocks | cube-net chart "+chart);
    }
}
