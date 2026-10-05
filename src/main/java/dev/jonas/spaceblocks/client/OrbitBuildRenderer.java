package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.network.FlatNetwork;
import dev.jonas.spaceblocks.surface.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.*;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

/** Textured voxel silhouettes for player edits outside detailed portal rendering range. */
public final class OrbitBuildRenderer {
    private static final LinkedHashMap<Long,BlockState> BLOCKS=new LinkedHashMap<>();
    private static final EnumMap<Face,VertexBuffer> MESHES=new EnumMap<>(Face.class);
    private static net.minecraft.resources.ResourceLocation dimension;
    private static final Set<Face> DIRTY=EnumSet.noneOf(Face.class);
    public static int drawn,visibleBlocks;
    public static void receive(FlatNetwork.Scene packet) {
        if(!Objects.equals(dimension,packet.dimension())||packet.reset()){clear();dimension=packet.dimension();}
        for(var e:packet.blocks()) {
            BlockPos address=BlockPos.of(e.position());if(FlatClient.surface==null)continue;
            int face=Math.floorDiv(address.getX(),FlatClient.surface.faceSize());if(face<0||face>5)continue;
            BLOCKS.put(e.position(),Block.stateById(e.state()));DIRTY.add(Face.values()[face]);
        }
        while(BLOCKS.size()>65536)BLOCKS.remove(BLOCKS.keySet().iterator().next());
    }
    public static void clear(){for(var b:MESHES.values())b.close();MESHES.clear();BLOCKS.clear();DIRTY.clear();}
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if(!FlatClient.active()){if(!BLOCKS.isEmpty()||!MESHES.isEmpty())clear();dimension=null;return;}
        if(DIRTY.isEmpty())return;var face=DIRTY.iterator().next();DIRTY.remove(face);compile(face);
    }
    private static void compile(Face face) {
        var m=Minecraft.getInstance();if(!m.level.dimension().location().equals(dimension))return;var d=FlatClient.surface;
        try(var data=new ByteBufferBuilder(65536)) {
            var buffer=new BufferBuilder(data,VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);
            for(var entry:BLOCKS.entrySet()) {
                var state=entry.getValue();if(state.isAir())continue;BlockPos address=BlockPos.of(entry.getKey());var p=d.fromAddress(address);
                if(p.face()!=face||address.getY()<d.height(p))continue;
                Vec3 storage=d.storage(p,address.getY());float x=(float)Math.floor(storage.x),y=address.getY(),z=(float)Math.floor(storage.z);
                var sprite=m.getBlockRenderer().getBlockModel(state).getParticleIcon(net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
                float u0=sprite.getU0(),u1=sprite.getU1(),v0=sprite.getV0(),v1=sprite.getV1();
                // A distant LOD uses the block's texture and bounding cube, not its full baked model.
                float[][][] quads={{{0,0,0},{1,0,0},{1,1,0},{0,1,0}},{{1,0,1},{0,0,1},{0,1,1},{1,1,1}},{{0,0,1},{0,0,0},{0,1,0},{0,1,1}},{{1,0,0},{1,0,1},{1,1,1},{1,1,0}},{{0,1,0},{1,1,0},{1,1,1},{0,1,1}},{{0,0,1},{1,0,1},{1,0,0},{0,0,0}}};
                for(int q=0;q<6;q++)for(int i=0;i<4;i++) {float[] v=quads[q][3-i];float shade=q==4?1:q==5?.5f:.8f;
                    buffer.addVertex(x+v[0],y+v[1],z+v[2]).setColor(shade,shade,shade,1).setUv(i==0||i==3?u0:u1,i<2?v1:v0).setUv2(240,240).setNormal(0,1,0);
                }
            }
            var mesh=buffer.build();var old=MESHES.remove(face);if(old!=null)old.close();if(mesh!=null){var vbo=new VertexBuffer(VertexBuffer.Usage.STATIC);vbo.bind();vbo.upload(mesh);MESHES.put(face,vbo);VertexBuffer.unbind();}
        }
        visibleBlocks=(int)BLOCKS.entrySet().stream().filter(e->!e.getValue().isAir()).count();
    }
    public static void render(Matrix4f view,Matrix4f projection) {
        if(!FlatClient.active()||MESHES.isEmpty()||!Minecraft.getInstance().level.dimension().location().equals(dimension))return;
        Vec3 eye=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();float alpha=FlatShaders.smooth(96,224,eye.y-64);if(alpha<=0)return;
        var d=FlatClient.surface;var type=RenderType.solid();var shader=FlatShaders.current(type);type.setupRenderState();FlatShaders.prepare(shader);
        try {
            shader.getUniform("CachedMode").set(3);shader.getUniform("CachedMatrix").set(new Matrix4f());shader.CHUNK_OFFSET.set(0f,0f,0f);shader.getUniform("DetailVisibility").set(alpha);
            for(var e:MESHES.entrySet()) {
                shader.getUniform("PortalSourceFace").set(e.getKey().ordinal());shader.getUniform("PortalSourceOrigin").set((float)d.originX(e.getKey()),0,(float)d.originZ(e.getKey()));
                e.getValue().bind();e.getValue().drawWithShader(view,projection,shader);drawn++;
            }
        } finally {shader.getUniform("CachedMode").set(0);VertexBuffer.unbind();type.clearRenderState();}
    }
}
