package dev.jonas.spaceblocks.surface;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;
class FlatAtlasTest {
    @Test void copiesAtAllJoinedEdgesHaveTheSamePersistentAddress() {
        var d=new FlatDefinition(1024,512,1);
        for(Face f:Face.values())for(Edge edge:Edge.values())for(double parallel:new double[]{.5,17.5,512.5,1023.5}) {
            Position outside=switch(edge){case WEST->new Position(f,-.5,parallel);case EAST->new Position(f,1024.5,parallel);case NORTH->new Position(f,parallel,-.5);case SOUTH->new Position(f,parallel,1024.5);};
            var core=canonical(outside,1024);var address=d.address(outside,128);
            assertEquals(address,d.address(core,128));assertEquals(core,d.fromAddress(address));
            var copies=FlatMotion.aliases(d,core);assertTrue(copies.contains(outside),f+" "+edge);
            for(var copy:copies){assertEquals(address,d.address(copy,128));assertEquals(copy,d.locate(d.storage(copy,128).x,d.storage(copy,128).z));}
        }
    }
    @Test void relaxedSphereHasContinuousWalkingTangentsAtOrdinaryEdges() {
        double n=1024,h=.01;
        for(Face f:Face.values())for(Edge edge:Edge.values())for(double parallel:new double[]{128,512,896}) {
            Position a=position(f,edge,-h,parallel,n),b=position(f,edge,0,parallel,n),c=position(f,edge,h,parallel,n);
            Vec3 incoming=sphere(b,n,n,0).subtract(sphere(a,n,n,0)).normalize(),outgoing=sphere(c,n,n,0).subtract(sphere(b,n,n,0)).normalize();
            assertTrue(incoming.dot(outgoing)>.995,f+" "+edge+" tangent alignment="+incoming.dot(outgoing));
        }
    }
    private Position position(Face f,Edge e,double over,double p,double n) {return switch(e){case WEST->new Position(f,-over,p);case EAST->new Position(f,n+over,p);case NORTH->new Position(f,p,-over);case SOUTH->new Position(f,p,n+over);};}
}
