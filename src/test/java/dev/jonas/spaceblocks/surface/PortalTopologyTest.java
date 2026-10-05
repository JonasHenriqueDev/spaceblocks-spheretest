package dev.jonas.spaceblocks.surface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;
import net.minecraft.world.phys.Vec3;
public class PortalTopologyTest {
    @Test void allDirectedPortalCamerasAndBlocksAgreeWithTransport() {
        for(Face face:Face.values())for(Edge edge:Edge.values()) {
            Position outside=switch(edge){case WEST->new Position(face,-8,25);case EAST->new Position(face,72,25);case NORTH->new Position(face,25,-8);case SOUTH->new Position(face,25,72);};
            var destination=canonical(outside,64);
            var views=PortalTopology.views(new Position(face,32,32),64,96);
            assertTrue(views.stream().anyMatch(v->{if(v.face()!=destination.face())return false;var point=v.project(destination.u(),77,destination.v());return point.distanceTo(new Vec3(outside.u(),77,outside.v()))<1e-6;}),face+" "+edge);
            for(var v:views){var p=v.project(17.5,77,21.5);var inverse=v.camera(p.x,p.z);assertEquals(17.5,inverse.u(),1e-8);assertEquals(21.5,inverse.v(),1e-8);assertEquals(77,p.y);}
        }
    }
    @Test void reliefIsSharedAtAllFaceEdgesAndVariesAcrossPlanet() {
        var d=new FlatDefinition(1024,512,20261006,false,true);int min=999,max=-999;
        for(Face face:Face.values()){
            for(int u=0;u<1024;u+=64)for(int v=0;v<1024;v+=64){int h=d.height(new Position(face,u,v));min=Math.min(min,h);max=Math.max(max,h);}
            for(Edge edge:Edge.values())for(int t=0;t<1024;t+=32){var p=switch(edge){case WEST->new Position(face,0,t);case EAST->new Position(face,1024,t);case NORTH->new Position(face,t,0);case SOUTH->new Position(face,t,1024);};assertEquals(d.height(p),d.height(cross(p,Vec3.ZERO,0,1024,edge).position()));}
        }
        assertTrue(max-min>40);assertTrue(min>=20&&max<=112);
    }
}
