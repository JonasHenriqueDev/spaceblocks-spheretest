package dev.jonas.spaceblocks.surface;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

class CubeTopologyTest {
    private static final double SIZE=64;
    @Test void allTwentyFourDirectedEdgesPreservePositionSpeedAndVerticalAxis() {
        for(Face face:Face.values()) for(Edge edge:Edge.values()) for(double parallel:new double[]{.5,13.5,32.5,63.5}) {
            Position p=outside(face,edge,.25,parallel);
            Vec3 speed=new Vec3(.23,-.08,.19);
            Transition crossed=wrap(p,speed,37,SIZE);
            assertEquals(1,crossed.crossings());
            assertNotEquals(face,crossed.position().face());
            assertTrue(crossed.position().u()>=0 && crossed.position().u()<SIZE);
            assertTrue(crossed.position().v()>=0 && crossed.position().v()<SIZE);
            assertEquals(speed.lengthSqr(),crossed.velocity().lengthSqr(),1e-12);
            assertEquals(speed.y,crossed.velocity().y,0);
            double yawChange=crossed.yaw()-37;
            assertEquals(Math.rint(yawChange/90),yawChange/90,1e-6);
        }
    }
    @Test void sphereIsContinuousOnEveryJoinedEdge() {
        for(Face face:Face.values()) for(Edge edge:Edge.values()) for(double parallel:new double[]{.5,13.5,32.5,63.5}) {
            Position before=outside(face,edge,-1e-6,parallel);
            Position after=canonical(outside(face,edge,1e-6,parallel),SIZE);
            assertTrue(sphere(before,SIZE,256,0).distanceTo(sphere(after,SIZE,256,0))<.00005,
                    face+" / "+edge+" / "+parallel);
        }
    }
    @Test void everyEdgeCanBeCrossedBackWithoutChangingBlockAddress() {
        for(Face face:Face.values()) for(Edge edge:Edge.values()) {
            Position start=outside(face,edge,.25,18.5);
            var forward=wrap(start,new Vec3(.2,.1,.3),29,SIZE);
            Position p=forward.position();
            Edge back=p.u()<1?Edge.WEST:p.u()>SIZE-1?Edge.EAST:p.v()<1?Edge.NORTH:Edge.SOUTH;
            var returned=cross(outside(p.face(),back,.25,back==Edge.WEST||back==Edge.EAST?p.v():p.u()),forward.velocity(),forward.yaw(),SIZE,back);
            assertEquals(face,returned.position().face());
            assertEquals(new Vec3(.2,.1,.3),returned.velocity());
            assertEquals(29,returned.yaw(),1e-5);
        }
    }
    @Test void straightEquatorialWalkClosesAfterFourFaces() {
        Position p=new Position(Face.FRONT,17.5,32.5);
        Vec3 speed=new Vec3(1,0,0);float yaw=-90;
        int seams=0;
        for(int i=0;i<SIZE*4;i++) {
            var step=wrap(new Position(p.face(),p.u()+speed.x,p.v()+speed.z),speed,yaw,SIZE);
            p=step.position();speed=step.velocity();yaw=step.yaw();seams+=step.crossings();
        }
        assertEquals(4,seams);assertEquals(Face.FRONT,p.face());
        assertEquals(17.5,p.u(),1e-8);assertEquals(32.5,p.v(),1e-8);
        assertEquals(new Vec3(1,0,0),speed);assertEquals(-90,yaw,1e-5);
    }
    @Test void projectionIsSphericalAndHasNoTopBottomSeam() {
        for(Face face:Face.values()) for(int u=0;u<=64;u+=8) for(int v=0;v<=64;v+=8)
            assertEquals(256,sphere(new Position(face,u,v),SIZE,256,0).length(),1e-9);
    }
    @Test void cornerLoopReturnsToSameChartWithNinetyDegreeHeadingTransport() {
        var a=cross(new Position(Face.FRONT,64.25,.25),new Vec3(1,0,0),0,SIZE,Edge.EAST);
        var b=cross(new Position(a.position().face(),a.position().u(),-.25),a.velocity(),a.yaw(),SIZE,Edge.NORTH);
        var c=cross(new Position(b.position().face(),b.position().u(),64.25),b.velocity(),b.yaw(),SIZE,Edge.SOUTH);
        assertEquals(Face.FRONT,c.position().face());
        assertEquals(63.75,c.position().u(),1e-9);assertEquals(.25,c.position().v(),1e-9);
        assertEquals(90,Math.abs(c.yaw()),1e-6);
        assertEquals(1,c.velocity().length(),1e-9);
    }
    private static Position outside(Face face,Edge edge,double overflow,double parallel) {
        return switch(edge) {
            case WEST->new Position(face,-overflow,parallel);
            case EAST->new Position(face,SIZE+overflow,parallel);
            case NORTH->new Position(face,parallel,-overflow);
            case SOUTH->new Position(face,parallel,SIZE+overflow);
        };
    }
}
