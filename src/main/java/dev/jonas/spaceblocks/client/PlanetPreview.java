package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.PlanetDefinition;
import dev.jonas.spaceblocks.physics.RadialMotion;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Distant body silhouette beyond the ordinary chunk view distance; terrain still draws over it. */
final class PlanetPreview {
    private static PlanetDefinition cached;
    private static Vec3[][] points;
    private static final int LONGITUDES=96, LATITUDES=48;
    private static void prepare(PlanetDefinition body) {
        if(body.equals(cached)) return;
        cached=body; points=new Vec3[LATITUDES+1][LONGITUDES+1];
        for(int lat=0;lat<=LATITUDES;lat++) for(int lon=0;lon<=LONGITUDES;lon++) {
            double a=Math.PI*lat/LATITUDES, b=Math.PI*2*lon/LONGITUDES;
            Vec3 n=new Vec3(Math.sin(a)*Math.cos(b),Math.cos(a),Math.sin(a)*Math.sin(b));
            double r=body.surfaceRadius(body.centerX()+n.x,body.centerY()+n.y,body.centerZ()+n.z);
            points[lat][lon]=n.scale(r);
        }
    }
    static void draw(Matrix4f view, Camera camera) {
        var player=Minecraft.getInstance().player;
        if(player==null) return;
        var session=RadialMotion.session(player);
        if(session==null) return;
        PlanetDefinition body=session.body;
        Vec3 center=new Vec3(body.centerX(),body.centerY(),body.centerZ());
        Vec3 toCamera=camera.getPosition().subtract(center);
        double distance=toCamera.length(), altitude=distance-body.radius();
        if(altitude<=32) return;
        prepare(body);
        int alpha=(int)(255*Math.min(1,(altitude-32)/64));
        float scale=(float)(100/distance);
        Vec3 sun=new Vec3(-.4,.8,.5).normalize();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        try {
            BufferBuilder buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(int lat=0;lat<LATITUDES;lat++) for(int lon=0;lon<LONGITUDES;lon++) {
                Vec3 a=points[lat][lon],b=points[lat+1][lon],c=points[lat+1][lon+1],d=points[lat][lon+1];
                Vec3 middle=a.add(b).add(c).add(d).scale(.25);
                Vec3 normal=middle.normalize();
                if(normal.dot(toCamera.subtract(middle))<=0) continue;
                double light=.35+.65*Math.max(0,normal.dot(sun));
                int red=(int)(95*light),green=(int)(128*light),blue=(int)(54*light);
                vertex(buffer,view,a.subtract(toCamera),scale,red,green,blue,alpha);
                vertex(buffer,view,b.subtract(toCamera),scale,red,green,blue,alpha);
                vertex(buffer,view,c.subtract(toCamera),scale,red,green,blue,alpha);
                vertex(buffer,view,d.subtract(toCamera),scale,red,green,blue,alpha);
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally { RenderSystem.disableBlend(); }
    }
    private static void vertex(BufferBuilder buffer,Matrix4f view,Vec3 p,float scale,int r,int g,int b,int alpha) {
        buffer.addVertex(view,(float)p.x*scale,(float)p.y*scale,(float)p.z*scale).setColor(r,g,b,alpha);
    }
}
