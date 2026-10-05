package dev.jonas.spaceblocks.surface;
import dev.jonas.spaceblocks.client.FlatVisual;
import dev.jonas.spaceblocks.physics.GravityFrame;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;
class CornerVisualTest {
    @Test void cornerSelectionInvertsTheVisualMappingOnEveryFace() {
        var d=new FlatDefinition(1024,512,1);
        for(Face face:Face.values())for(double u:new double[]{3.5,1020.5})for(double v:new double[]{3.5,1020.5}) {
            var p=new Position(face,u,v);Vec3 eye=d.storage(p,66.62),up=sphere(p,1024,1,0),east=GravityFrame.tangent(face.uAxis,up).normalize();
            var view=new FlatVisual.View(d,eye,new GravityFrame(up,east.cross(up).normalize()),1);
            for(Vec3 offset:new Vec3[]{new Vec3(.7,-1.6,.3),new Vec3(-.8,-1.3,.6),new Vec3(.2,.4,-.7)}) {
                Vec3 point=eye.add(offset),restored=view.unproject(view.project(point),eye.add(offset.scale(.8)));
                assertTrue(point.distanceTo(restored)<.002,face+" inverse error="+point.distanceTo(restored));
            }
        }
    }
}
