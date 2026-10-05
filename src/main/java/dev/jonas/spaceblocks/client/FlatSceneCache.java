package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.mixin.client.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

/** A transient copy of the actual visible chunk meshes, retained through chart rebasing. */
public final class FlatSceneCache {
    private record Mesh(RenderType type,BlockPos origin,VertexBuffer buffer) {}
    private static final List<Mesh> meshes=new ArrayList<>();
    private static Matrix4f transform=new Matrix4f();
    private static int created;
    public static int captures,framesDrawn;
    public static void clear() { for(var mesh:meshes) mesh.buffer.close();meshes.clear(); }
    public static void capture(Vec3 old,Vec3 next,Vec3 ex,Vec3 ez) {
        clear();var minecraft=Minecraft.getInstance();
        transform=new Matrix4f().translation((float)next.x,(float)next.y,(float)next.z);
        Matrix4f rotation=new Matrix4f().m00((float)ex.x).m02((float)ex.z).m20((float)ez.x).m22((float)ez.z);
        transform.mul(rotation).translate((float)-old.x,(float)-old.y,(float)-old.z);
        for(var section:((FlatRendererAccess)minecraft.levelRenderer).spaceblocks$visible()) for(RenderType type:RenderType.chunkBufferLayers()) {
            if(section.getCompiled().isEmpty(type)) continue;
            VertexBuffer source=section.getBuffer(type);var access=(FlatBufferAccess)source;
            if(source.isInvalid()||access.spaceblocks$indexCount()==0) continue;
            int id=access.spaceblocks$vertexId();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,id);
            int bytes=GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER,GL15.GL_BUFFER_SIZE);if(bytes<=0) continue;
            try(var builder=new ByteBufferBuilder(bytes)) {
                    MemoryUtil.memSet(builder.reserve(bytes),0,bytes);
                    int vertices=bytes/source.getFormat().getVertexSize();
                    var state=new MeshData.DrawState(source.getFormat(),vertices,access.spaceblocks$indexCount(),access.spaceblocks$mode(),VertexFormat.IndexType.least(vertices));
                    var clone=new VertexBuffer(VertexBuffer.Usage.STATIC);clone.bind();clone.upload(new MeshData(builder.build(),state));
                    org.lwjgl.opengl.GL31.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER,id);
                    org.lwjgl.opengl.GL31.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER,((FlatBufferAccess)clone).spaceblocks$vertexId());
                    org.lwjgl.opengl.GL31.glCopyBufferSubData(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER,org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER,0,0,bytes);
                    org.lwjgl.opengl.GL31.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER,0);
                    org.lwjgl.opengl.GL31.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER,0);
                    meshes.add(new Mesh(type,section.getOrigin().immutable(),clone));
            }
        }
        VertexBuffer.unbind();created=minecraft.player.tickCount;captures++;
    }
    public static void render(RenderType type,Matrix4f view,Matrix4f projection) {
        var m=Minecraft.getInstance();if(!FlatClient.active()||meshes.isEmpty()) return;
        if(m.player.tickCount-created>600) { clear();return; }
        // Retire each old section only when its newly addressed counterpart is actually compiled.
        var visible=((FlatRendererAccess)m.levelRenderer).spaceblocks$visible();
        for(var iterator=meshes.iterator();iterator.hasNext();) {
            Mesh mesh=iterator.next();if(mesh.type!=type)continue;
            var center=transform.transformPosition(new org.joml.Vector3f(mesh.origin.getX()+8,mesh.origin.getY()+8,mesh.origin.getZ()+8));
            BlockPos target=new BlockPos((int)Math.floor(center.x/16)*16,(int)Math.floor(center.y/16)*16,(int)Math.floor(center.z/16)*16);
            var targetChunk=m.level.getChunkSource().getChunk(target.getX()>>4,target.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
            if(targetChunk==null||targetChunk instanceof net.minecraft.world.level.chunk.EmptyLevelChunk)continue;
            for(var section:visible)if(section.getOrigin().equals(target)&&section.getCompiled()!=net.minecraft.client.renderer.chunk.SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
                mesh.buffer.close();iterator.remove();break;
            }
        }
        type.setupRenderState();var shader=FlatShaders.current(type);if(shader==null) { type.clearRenderState();return; }
        FlatShaders.prepare(shader);shader.getUniform("CachedMode").set(1);shader.getUniform("CachedMatrix").set(transform);
        boolean drew=false;
        for(var mesh:meshes) if(mesh.type==type) {
            shader.CHUNK_OFFSET.set((float)mesh.origin.getX(),(float)mesh.origin.getY(),(float)mesh.origin.getZ());
            mesh.buffer.bind();mesh.buffer.drawWithShader(view,projection,shader);
            drew=true;
        }
        if(drew)framesDrawn++;shader.getUniform("CachedMode").set(0);VertexBuffer.unbind();type.clearRenderState();
    }
}

