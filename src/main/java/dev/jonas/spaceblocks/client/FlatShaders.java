package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.jonas.spaceblocks.SpaceBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import dev.jonas.spaceblocks.physics.GravityFrame;
import java.io.IOException;

public final class FlatShaders {
    private static ShaderInstance solid,cutout,mipped,translucent;
    private static int sphereTexture;
    public static void register(RegisterShadersEvent event) throws IOException {
        uploadSphereMap();
        event.registerShader(new ShaderInstance(event.getResourceProvider(),SpaceBlocks.id("flat_solid"),DefaultVertexFormat.BLOCK),s->solid=s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),SpaceBlocks.id("flat_cutout"),DefaultVertexFormat.BLOCK),s->cutout=s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),SpaceBlocks.id("flat_cutout_mipped"),DefaultVertexFormat.BLOCK),s->mipped=s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),SpaceBlocks.id("flat_translucent"),DefaultVertexFormat.BLOCK),s->translucent=s);
    }
    private static void uploadSphereMap() {
        if(sphereTexture!=0)com.mojang.blaze3d.systems.RenderSystem.deleteTexture(sphereTexture);
        sphereTexture=com.mojang.blaze3d.platform.GlStateManager._genTexture();
        com.mojang.blaze3d.systems.RenderSystem.bindTexture(sphereTexture);
        int width=dev.jonas.spaceblocks.surface.SphereProjection.WIDTH;
        var data=org.lwjgl.system.MemoryUtil.memAllocFloat(width*width*6*3);
        try {
            for(var face:dev.jonas.spaceblocks.surface.CubeTopology.Face.values())data.put(dev.jonas.spaceblocks.surface.SphereProjection.faceData(face));
            data.flip();org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL30.GL_RGB32F,width,width*6,0,org.lwjgl.opengl.GL11.GL_RGB,org.lwjgl.opengl.GL11.GL_FLOAT,data);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,org.lwjgl.opengl.GL11.GL_LINEAR);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,org.lwjgl.opengl.GL11.GL_LINEAR);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        } finally {org.lwjgl.system.MemoryUtil.memFree(data);com.mojang.blaze3d.systems.RenderSystem.bindTexture(0);}
    }
    public static ShaderInstance current(RenderType type) {
        return type==RenderType.solid()?solid:type==RenderType.cutout()?cutout:type==RenderType.cutoutMipped()?mipped:type==RenderType.translucent()?translucent:null;
    }
    public static ShaderInstance choose(RenderType type) {
        if(!FlatClient.active()) return null;var shader=current(type);if(shader!=null) prepare(shader);return shader;
    }
    public static float smooth(double a,double b,double value) { double t=Math.max(0,Math.min(1,(value-a)/(b-a)));return (float)(t*t*(3-2*t)); }
    public static float morph(double y) { return smooth(48,240,y-64); }
    public static float corner(dev.jonas.spaceblocks.surface.CubeTopology.Position p) {
        double size=FlatClient.surface.faceSize();
        double x=Math.min(Math.abs(p.u()),Math.abs(size-p.u())),z=Math.min(Math.abs(p.v()),Math.abs(size-p.v()));
        double radius=Math.min(48,size*.125);
        return 1-smooth(radius*.25,radius,Math.hypot(x,z));
    }
    public static void prepare(ShaderInstance shader) {
        var d=FlatClient.surface;Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var p=d.locate(camera.x,camera.z);if(p==null) return;
        FlatClient.updateFrame(p);GravityFrame frame=FlatClient.frame;
        vector(shader,"FlatCamera",camera);vector(shader,"ChartOrigin",new Vec3(d.originX(p.face()),0,d.originZ(p.face())));
        
        vector(shader,"FrameEast",frame.east());vector(shader,"FrameUp",frame.up());vector(shader,"FrameSouth",frame.north());
        shader.setSampler("SphereMap",sphereTexture);
        shader.getUniform("FaceIndex").set(p.face().ordinal());shader.getUniform("GuardSize").set((float)d.guardSize());
        shader.getUniform("FaceSize").set((float)d.faceSize());shader.getUniform("PlanetRadius").set((float)d.radius());
        shader.getUniform("FlatMorph").set(morph(camera.y));shader.getUniform("CornerMorph").set(corner(p));shader.getUniform("CachedMode").set(0);
        vector(shader,"PortalSourceOrigin",new Vec3(d.originX(p.face()),0,d.originZ(p.face())));shader.getUniform("PortalSourceFace").set(p.face().ordinal());
        shader.getUniform("DetailVisibility").set(1-smooth(128,240,camera.y-64));
    }
    private static void vector(ShaderInstance shader,String name,Vec3 value) { shader.getUniform(name).set((float)value.x,(float)value.y,(float)value.z); }
}
