package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Random;

public final class SpaceEffects extends DimensionSpecialEffects {
    private final Vector3f[] vertices;
    private final int[] brightness;

    public SpaceEffects() {
        super(Float.NaN, false, SkyType.NONE, false, true);
        Random random = new Random(20261004L);
        vertices = new Vector3f[900 * 4];
        brightness = new int[900];
        for (int i = 0; i < 900; i++) {
            double azimuth = random.nextDouble() * Math.PI * 2;
            double vertical = random.nextDouble() * 2 - 1;
            double horizontal = Math.sqrt(1 - vertical * vertical);
            Vector3f direction = new Vector3f((float) (horizontal * Math.cos(azimuth)),
                    (float) vertical, (float) (horizontal * Math.sin(azimuth)));
            Vector3f reference = Math.abs(direction.y) < 0.95 ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            float size = 0.07f + random.nextFloat() * 0.12f;
            Vector3f right = new Vector3f(direction).cross(reference).normalize().mul(size);
            Vector3f up = new Vector3f(right).cross(direction).normalize().mul(size);
            Vector3f center = new Vector3f(direction).mul(100);
            vertices[i * 4] = new Vector3f(center).sub(right).sub(up);
            vertices[i * 4 + 1] = new Vector3f(center).add(right).sub(up);
            vertices[i * 4 + 2] = new Vector3f(center).add(right).add(up);
            vertices[i * 4 + 3] = new Vector3f(center).sub(right).add(up);
            brightness[i] = 130 + random.nextInt(126);
        }
    }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return Vec3.ZERO; }
    @Override public boolean isFoggyAt(int x, int y) { return false; }
    @Override public float[] getSunriseColor(float time, float partialTick) { return null; }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView,
                             Camera camera, Matrix4f projection, boolean foggy, Runnable setupFog) {
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        float oldFogStart = RenderSystem.getShaderFogStart();
        float oldFogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(100_000);
        RenderSystem.setShaderFogEnd(1_000_000);
        try {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < vertices.length; i++) {
                Vector3f point = vertices[i];
                if(FlatClient.active() && FlatClient.frame!=null) {
                    Vec3 star=new Vec3(point);var frame=FlatClient.frame;
                    point=new Vector3f((float)star.dot(frame.east()),(float)star.dot(frame.up()),(float)star.dot(frame.north()));
                }
                int color = brightness[i / 4];
                buffer.addVertex(modelView, point.x, point.y, point.z).setColor(color, color, color, 255);
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            if(FlatClient.active()){
                RenderSystem.enableDepthTest();RenderSystem.depthMask(FlatShaders.morph(camera.getPosition().y)>.99);FlatPlanetMesh.draw(modelView,camera);RenderSystem.depthMask(false);
            }
            else PlanetPreview.draw(modelView, camera);
        } finally {
            RenderSystem.setShaderFogStart(oldFogStart);
            RenderSystem.setShaderFogEnd(oldFogEnd);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        }
        return true;
    }
}
