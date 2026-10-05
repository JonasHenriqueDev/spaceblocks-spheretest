package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.surface.*;
import dev.jonas.spaceblocks.mixin.client.FlatRendererAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.*;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

/** Live source geometry, virtual camera charts and stencil-masked invisible portals.
 * Independently written; conceptual credit: Immersive Portals / iPortalTeam / qouteall.
 */
public final class PortalRenderer {
    private record Section(BlockPos origin,EnumMap<Layer,VertexBuffer> buffers) {}
    private enum Layer { SOLID,CUTOUT,MIPPED,TRANSLUCENT;
        RenderType type(){return switch(this){case SOLID->RenderType.solid();case CUTOUT->RenderType.cutout();case MIPPED->RenderType.cutoutMipped();case TRANSLUCENT->RenderType.translucent();};}
        static Layer of(RenderType type){for(var l:values())if(l.type()==type)return l;return null;}
    }
    private static final LinkedHashMap<BlockPos,Section> CACHE=new LinkedHashMap<>(128,.75f,true);
    private static final Set<BlockPos> DIRTY=new HashSet<>();
    private static final ArrayDeque<BlockPos> PENDING=new ArrayDeque<>();
    private static List<PortalTopology.View> views=List.of();
    private static Object level;
    private static int planned=-1;
    public static int compiled,remoteDraws,rootFallbacks,stencilPasses;
    public static final Set<Face> drawnFaces=EnumSet.noneOf(Face.class);
    public static void clear(){for(var section:CACHE.values())for(var buffer:section.buffers.values())buffer.close();CACHE.clear();DIRTY.clear();PENDING.clear();views=List.of();planned=-1;}
    public static void changed(int x,int y,int z){if(FlatClient.active())DIRTY.add(new BlockPos(x*16,y*16,z*16));}
    public static void chunkChanged(int x,int z){for(var p:CACHE.keySet())if(p.getX()>>4==x&&p.getZ()>>4==z)DIRTY.add(p);planned=-1;}
    private static void plan() {
        var m=Minecraft.getInstance();if(level!=m.level){clear();level=m.level;}
        if(planned==m.player.tickCount)return;planned=m.player.tickCount;
        var d=FlatClient.surface;var p=d.locate(m.player.getX(),m.player.getZ());if(p==null)return;
        int range=Math.min(10,m.options.getEffectiveRenderDistance())*16;
        views=PortalTopology.views(p,d.faceSize(),range);PENDING.clear();if(views.size()==1)return;Set<BlockPos> queued=new HashSet<>();
        for(var candidate:PortalTopology.chunks(d,p,range)) {
            var chunk=m.level.getChunkSource().getChunk(candidate.x(),candidate.z(),ChunkStatus.FULL,false);
            if(chunk==null||chunk instanceof EmptyLevelChunk)continue;
            for(int i=0;i<chunk.getSectionsCount();i++) {
                BlockPos origin=new BlockPos(candidate.x()*16,chunk.getSectionYFromSectionIndex(i)*16,candidate.z()*16);
                if(chunk.getSection(i).hasOnlyAir()) {var old=CACHE.remove(origin);if(old!=null)for(var b:old.buffers.values())b.close();continue;}
                if((!CACHE.containsKey(origin)||DIRTY.contains(origin))&&queued.add(origin))PENDING.add(origin);
            }
        }
    }
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if(!FlatClient.active()){if(!CACHE.isEmpty())clear();level=null;return;}
        Minecraft.getInstance().getMainRenderTarget().enableStencil();
        plan();long until=System.nanoTime()+6_000_000;
        for(int i=0;i<3&&!PENDING.isEmpty();i++) {
            BlockPos pos=PENDING.removeFirst();compile(pos);if(System.nanoTime()>until)break;
        }
        while(CACHE.size()>768){var iterator=CACHE.entrySet().iterator();var old=iterator.next().getValue();iterator.remove();for(var b:old.buffers.values())b.close();}
    }
    private static void compile(BlockPos origin) {
        var m=Minecraft.getInstance();var chunk=m.level.getChunkSource().getChunk(origin.getX()>>4,origin.getZ()>>4,ChunkStatus.FULL,false);
        if(chunk==null||chunk instanceof EmptyLevelChunk)return;
        var d=FlatClient.surface;var chart=d.locate(origin.getX()+8,origin.getZ()+8);if(chart==null||chart.u()<0||chart.v()<0||chart.u()>=d.faceSize()||chart.v()>=d.faceSize())return;
        EnumMap<Layer,ByteBufferBuilder> storage=new EnumMap<>(Layer.class);EnumMap<Layer,BufferBuilder> builders=new EnumMap<>(Layer.class);EnumMap<Layer,VertexBuffer> result=new EnumMap<>(Layer.class);
        try {
            for(var layer:Layer.values()){var data=new ByteBufferBuilder(65536);storage.put(layer,data);builders.put(layer,new BufferBuilder(data,VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK));}
            var pose=new PoseStack();var random=RandomSource.create(0);var block=m.getBlockRenderer();var pos=new BlockPos.MutableBlockPos();
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                pos.set(origin.getX()+x,origin.getY()+y,origin.getZ()+z);var state=chunk.getBlockState(pos);
                if(state.getRenderShape()!=RenderShape.MODEL)continue;
                random.setSeed(state.getSeed(pos));
                for(var type:block.getBlockModel(state).getRenderTypes(state,random,ModelData.EMPTY)) {
                    var layer=Layer.of(type);if(layer==null)continue;pose.pushPose();pose.translate(x,y,z);
                    block.renderBatched(state,pos,m.level,pose,builders.get(layer),true,random,ModelData.EMPTY,type);pose.popPose();
                }
            }
            for(var layer:Layer.values()) {var mesh=builders.get(layer).build();if(mesh==null)continue;var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(mesh);result.put(layer,buffer);}
            VertexBuffer.unbind();var old=CACHE.put(origin,new Section(origin,result));if(old!=null)for(var b:old.buffers.values())b.close();DIRTY.remove(origin);compiled++;
        } finally {for(var data:storage.values())data.close();}
    }
    public static void render(RenderType type,Matrix4f modelView,Matrix4f projection) {
        var layer=Layer.of(type);if(layer==null||!FlatClient.active())return;var m=Minecraft.getInstance();if(CACHE.isEmpty())return;
        var d=FlatClient.surface;Vec3 eye=m.gameRenderer.getMainCamera().getPosition();var p=d.locate(eye.x,eye.z);if(p==null)return;
        if(views.isEmpty()||views.getFirst().face()!=p.face()){planned=-1;plan();}
        var shader=FlatShaders.current(type);if(shader==null)return;
        Set<BlockPos> nativeSections=new HashSet<>();
        for(var s:((FlatRendererAccess)m.levelRenderer).spaceblocks$visible())if(!s.getCompiled().isEmpty(type))nativeSections.add(s.getOrigin());
        type.setupRenderState();FlatShaders.prepare(shader);
        try {
            // Already-compiled live destination geometry bridges the native renderer's relocation.
            for(var v:views)if(v.depth()==0)draw(v,layer,shader,modelView,projection,p,nativeSections,false);
            if(views.size()<2)return;
            // Curved corner charts are not bounded by a planar screen-space portal rectangle.
            // Their canonical fragment clips and the shared depth buffer delimit the live views.
            if(FlatShaders.corner(p)<.001f){
                m.getMainRenderTarget().enableStencil();m.getMainRenderTarget().bindWrite(false);
                stencil(modelView,eye,p,d);
                type.setupRenderState();FlatShaders.prepare(shader);GL11.glEnable(GL11.GL_STENCIL_TEST);GL11.glStencilMask(0);GL11.glStencilFunc(GL11.GL_EQUAL,1,255);GL11.glStencilOp(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_KEEP);
            }else{GL11.glDisable(GL11.GL_STENCIL_TEST);type.setupRenderState();FlatShaders.prepare(shader);}
            for(var v:views)if(v.depth()>0)draw(v,layer,shader,modelView,projection,p,nativeSections,true);
        } finally {GL11.glStencilMask(255);GL11.glDisable(GL11.GL_STENCIL_TEST);shader.getUniform("CachedMode").set(0);VertexBuffer.unbind();type.clearRenderState();}
    }
    private static void draw(PortalTopology.View v,Layer layer,ShaderInstance shader,Matrix4f modelView,Matrix4f projection,Position eye,Set<BlockPos> nativeSections,boolean remote) {
        var d=FlatClient.surface;
        Matrix4f matrix=new Matrix4f().translation((float)(d.originX(eye.face())+v.tx()),0,(float)(d.originZ(eye.face())+v.tz()));
        matrix.mul(new Matrix4f().m00((float)v.xx()).m20((float)v.xz()).m02((float)v.zx()).m22((float)v.zz())).translate((float)-d.originX(v.face()),0,(float)-d.originZ(v.face()));
        shader.getUniform("CachedMode").set(2);shader.getUniform("CachedMatrix").set(matrix);shader.getUniform("PortalSourceFace").set(v.face().ordinal());shader.getUniform("PortalSourceOrigin").set((float)d.originX(v.face()),0,(float)d.originZ(v.face()));
        for(var s:CACHE.values()) {
            var chart=d.locate(s.origin.getX()+8,s.origin.getZ()+8);if(chart==null||chart.face()!=v.face())continue;
            if(!remote&&nativeSections.contains(s.origin))continue;
            Vec3 target=v.project(chart.u(),s.origin.getY()+8,chart.v());if(Math.hypot(target.x-eye.u(),target.z-eye.v())>Math.min(10,Minecraft.getInstance().options.getEffectiveRenderDistance())*16+16)continue;
            var buffer=s.buffers.get(layer);if(buffer==null)continue;
            shader.CHUNK_OFFSET.set((float)s.origin.getX(),(float)s.origin.getY(),(float)s.origin.getZ());buffer.bind();buffer.drawWithShader(modelView,projection,shader);
            if(remote){remoteDraws++;drawnFaces.add(v.face());}else rootFallbacks++;
        }
    }
    private static void stencil(Matrix4f modelView,Vec3 eye,Position p,FlatDefinition d) {
        GL11.glEnable(GL11.GL_STENCIL_TEST);GL11.glStencilMask(255);GL11.glClearStencil(0);GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glStencilFunc(GL11.GL_ALWAYS,1,255);GL11.glStencilOp(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_REPLACE);
        RenderSystem.colorMask(false,false,false,false);RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            var buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);var visual=FlatVisual.view(eye);
            for(Edge edge:Edge.values())for(int t=0;t<d.faceSize();t+=32)for(int y=-64;y<960;y+=64) {
                for(int[] corner:new int[][]{{t,y},{t+32,y},{t+32,y+64},{t,y+64}}) {
                    double u=edge==Edge.WEST?0:edge==Edge.EAST?d.faceSize():corner[0];double v=edge==Edge.NORTH?0:edge==Edge.SOUTH?d.faceSize():corner[0];
                    Vec3 point=visual.project(d.storage(new Position(p.face(),u,v),corner[1]));
                    buffer.addVertex(modelView,(float)point.x,(float)point.y,(float)point.z).setColor(255,255,255,255);
                }
            }
            var mesh=buffer.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);stencilPasses++;
        } finally {RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);RenderSystem.enableCull();}
    }
}
