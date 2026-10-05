package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.surface.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

/** The distant representation shares the spherical addresses of the physical flat charts. */
public final class FlatPlanetMesh {
    private static final int GRID=48;
    private static FlatDefinition cached;
    private static Vec3[][] sphere,leaf;
    private static int[][] colors;
    private static void prepare(FlatDefinition d) {
        if(d.equals(cached))return;cached=d;
        sphere=new Vec3[6][(GRID+1)*(GRID+1)];leaf=new Vec3[6][(GRID+1)*(GRID+1)];colors=new int[6][(GRID+1)*(GRID+1)];
        for(Face face:Face.values()) for(int v=0;v<=GRID;v++) for(int u=0;u<=GRID;u++) {
            int i=v*(GRID+1)+u;double x=(double)u*d.faceSize()/GRID,z=(double)v*d.faceSize()/GRID;
            var p=new Position(face,Math.min(Math.nextDown((double)d.faceSize()),x),Math.min(Math.nextDown((double)d.faceSize()),z));
            int height=d.height(p)+1;
            // Use the exact face boundary for shared vertices; canonical() accepts both charts at the edge.
            sphere[face.ordinal()][i]=CubeTopology.sphere(new Position(face,x,z),d.faceSize(),d.radius(),height-64);
            leaf[face.ordinal()][i]=new Vec3(face.column*d.faceSize()+x,height,face.row*d.faceSize()+z);
            double illumination=.35+.65*Math.max(0,sphere[face.ordinal()][i].normalize().dot(new Vec3(.6,.75,.28).normalize()));
            int base=d.coloredFaces()?FlatDefinition.faceColor(face):0x5c8236;
            colors[face.ordinal()][i]=((int)((base>>16&255)*illumination)<<16)|((int)((base>>8&255)*illumination)<<8)|(int)((base&255)*illumination);
        }
    }
    public static void draw(Matrix4f modelView,Camera camera) {
        if(!FlatClient.active())return;var d=FlatClient.surface;Vec3 eye=camera.getPosition();
        float alpha=1;
        var p=d.locate(eye.x,eye.z);if(p==null)return;prepare(d);FlatClient.updateFrame(p);
        var frame=FlatClient.frame;float morph=FlatShaders.morph(eye.y);
        Vec3 eyeSphere=CubeTopology.sphere(p,d.faceSize(),d.radius(),eye.y-64);
        Vec3 eyeLeaf=new Vec3(p.face().column*d.faceSize()+p.u(),eye.y,p.face().row*d.faceSize()+p.v());
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            BufferBuilder buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(Face face:Face.values()) {
                Vec3[] shown=new Vec3[(GRID+1)*(GRID+1)];
                for(int i=0;i<shown.length;i++) {
                    Vec3 globe=sphere[face.ordinal()][i].subtract(eyeSphere);
                    Vec3 curved=new Vec3(globe.dot(frame.east()),globe.dot(frame.up()),globe.dot(frame.north()));
                    // A complete globe sits under the locally flattened detailed chunks.
                    // Folding the visible cross itself would expose its cut edges near corners.
                    shown[i]=curved;
                }
                for(int v=0;v<GRID;v++) for(int u=0;u<GRID;u++) {
                    int a=v*(GRID+1)+u,b=a+GRID+1,c=b+1,e=a+1;
                    Vec3 normal=shown[b].subtract(shown[a]).cross(shown[c].subtract(shown[b]));
                    if(normal.dot(shown[a].scale(-1))<=0)continue;
                    for(int i:new int[]{a,b,c,e}) {
                        Vec3 point=shown[i];int color=colors[face.ordinal()][i];
                        buffer.addVertex(modelView,(float)point.x,(float)point.y,(float)point.z).setColor(color>>16&255,color>>8&255,color&255,(int)(alpha*255));
                    }
                }
            }
            MeshData mesh=buffer.build();
            if(mesh!=null)BufferUploader.drawWithShader(mesh);
        } finally { RenderSystem.disableBlend(); }
    }
}
