package dev.jonas.spaceblocks.physics;

import dev.jonas.spaceblocks.PlanetDefinition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadialPhysicsTest {
    private final PlanetDefinition body = new PlanetDefinition(0, 96, 0, 64, 20261004);
    private final Vec3 center = new Vec3(0, 96, 0);

    private List<AABB> terrain(AABB bounds) { return terrain(body, bounds); }

    private List<AABB> terrain(PlanetDefinition body, AABB bounds) {
        List<AABB> result = new ArrayList<>();
        for (int x = (int) Math.floor(bounds.minX); x <= Math.floor(bounds.maxX); x++)
            for (int y = (int) Math.floor(bounds.minY); y <= Math.floor(bounds.maxY); y++)
                for (int z = (int) Math.floor(bounds.minZ); z <= Math.floor(bounds.maxZ); z++)
                    if (body.depth(x + 0.5, y + 0.5, z + 0.5) >= 0) result.add(new AABB(x,y,z,x+1,y+1,z+1));
        return result;
    }

    @Test void gravityPointsInwardOnAllSixSides() {
        for (Vec3 direction : List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),
                new Vec3(0,-1,0),new Vec3(0,0,1),new Vec3(0,0,-1))) {
            var initial = RadialPhysics.State.at(center.add(direction.scale(80)), body);
            var next = RadialPhysics.tick(initial, RadialPhysics.Input.IDLE, body, bounds -> List.of());
            assertTrue(next.velocity().dot(direction) < -0.07);
            assertEquals(0, GravityFrame.tangent(next.velocity(), direction).length(), 1e-9);
        }
    }

    @Test void settlesOnTopSideAndUndersideWithoutPenetration() {
        for (Vec3 direction : List.of(new Vec3(0,1,0),new Vec3(1,0,0),new Vec3(0,-1,0),new Vec3(1,1,1).normalize())) {
            var state = RadialPhysics.State.at(center.add(direction.scale(80)), body);
            for (int tick = 0; tick < 200; tick++) state = RadialPhysics.tick(state, RadialPhysics.Input.IDLE, body, this::terrain);
            assertTrue(state.grounded(), "not grounded: " + direction + " " + state);
            assertTrue(state.position().distanceTo(center) > 55, "fell into the planet");
            var resolved = RadialPhysics.resolve(state.position(), state.frame().up(), 1.8, this::terrain);
            assertTrue(resolved.position().distanceTo(state.position()) < 0.005, "penetrates terrain: " + direction);
        }
    }

    @Test void completesGreatCircleThroughBothPolesAndDiagonalVoxelSteps() {
        var state = RadialPhysics.State.at(new Vec3(0.5, 176, 0.5), body);
        for (int i = 0; i < 160; i++) state = RadialPhysics.tick(state, RadialPhysics.Input.IDLE, body, this::terrain);
        double lastAngle = Math.atan2(state.position().z, state.position().y - 96);
        double travelledAngle = 0;
        int grounded = 0;
        int ticks = 0;
        double maxPenetration = 0;
        for (; ticks < 6000 && travelledAngle < Math.PI * 2; ticks++) {
            Vec3 up = state.frame().up();
            Vec3 tangent = new Vec3(1,0,0).cross(up).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-tangent.dot(state.frame().east()), tangent.dot(state.frame().north())));
            state = RadialPhysics.tick(state, new RadialPhysics.Input(ticks,1,0,false,false,false,yaw,0), body, this::terrain);
            double angle = Math.atan2(state.position().z, state.position().y - 96);
            double delta = angle - lastAngle;
            if (delta > Math.PI) delta -= Math.PI * 2;
            if (delta < -Math.PI) delta += Math.PI * 2;
            travelledAngle += delta;
            lastAngle = angle;
            if (state.grounded()) grounded++;
            maxPenetration = Math.max(maxPenetration, RadialPhysics.resolve(state.position(), state.frame().up(),1.8,this::terrain).position().distanceTo(state.position()));
            assertTrue(state.position().distanceTo(center) > 55, "fell inside at tick " + ticks);
            assertTrue(state.position().distanceTo(center) < 75, "launched off the surface at tick " + ticks);
        }
        System.out.println("Full circuit: ticks="+ticks+" angle="+travelledAngle+" grounded="+grounded+" penetration="+maxPenetration+" position="+state.position());
        assertTrue(travelledAngle >= Math.PI * 2, "stuck after " + travelledAngle + " radians at " + state.position());
        assertTrue(grounded > ticks * 0.8, "unstable floor contact");
        assertTrue(maxPenetration < 0.025, "capsule intersects blocks");
    }

    @Test void jumpUsesRadialUpAndReturnsToGroundOnTheUnderside() {
        var state = RadialPhysics.State.at(center.add(0,-80,0),body);
        for (int tick=0; tick<200; tick++) state=RadialPhysics.tick(state,RadialPhysics.Input.IDLE,body,this::terrain);
        Vec3 start=state.position();
        state=RadialPhysics.tick(state,new RadialPhysics.Input(0,0,0,true,false,false,0,0),body,this::terrain);
        assertTrue(state.velocity().dot(state.frame().up())>0.3);
        assertTrue(state.position().y<start.y, "jump must point toward negative world Y underneath");
        double maxRise=0;
        for(int tick=0;tick<80;tick++) {
            state=RadialPhysics.tick(state,RadialPhysics.Input.IDLE,body,this::terrain);
            maxRise=Math.max(maxRise,start.y-state.position().y);
        }
        assertTrue(maxRise>0.8);
        assertTrue(state.grounded());
    }

    @Test void transportedFrameIsContinuousAcrossPoles() {
        GravityFrame frame = GravityFrame.at(new Vec3(0,1,0));
        for(int step=1; step<=2000;step++) {
            double angle=step*Math.PI*4/2000;
            Vec3 up=new Vec3(0,Math.cos(angle),Math.sin(angle));
            GravityFrame next=frame.transport(up);
            assertTrue(frame.north().dot(next.north())>0.999);
            assertEquals(0,next.north().dot(up),1e-9);
            assertEquals(1,next.north().length(),1e-9);
            frame=next;
        }
    }

    @Test void crossesOtherMeridianAndObliqueGreatCircle() {
        for(Vec3 axis:List.of(new Vec3(0,0,1),new Vec3(1,1,1).normalize())) {
            Vec3 startUp=GravityFrame.tangent(new Vec3(0,1,0),axis).normalize();
            Vec3 startForward=axis.cross(startUp).normalize();
            var state=RadialPhysics.State.at(center.add(startUp.scale(80)).add(0.3,0,0.3),body);
            for(int tick=0;tick<160;tick++) state=RadialPhysics.tick(state,RadialPhysics.Input.IDLE,body,this::terrain);
            Vec3 radial=state.position().subtract(center);
            double last=Math.atan2(radial.dot(startForward),radial.dot(startUp)),total=0;
            int tick=0;
            for(;tick<6000 && total<Math.PI*2;tick++) {
                Vec3 direction=axis.cross(state.frame().up()).normalize();
                float yaw=(float)Math.toDegrees(Math.atan2(-direction.dot(state.frame().east()),direction.dot(state.frame().north())));
                state=RadialPhysics.tick(state,new RadialPhysics.Input(tick,1,0,false,true,false,yaw,0),body,this::terrain);
                radial=state.position().subtract(center);
                double angle=Math.atan2(radial.dot(startForward),radial.dot(startUp));
                double delta=angle-last;
                if(delta>Math.PI)delta-=Math.PI*2;
                if(delta<-Math.PI)delta+=Math.PI*2;
                total+=delta;last=angle;
                assertTrue(radial.length()>55 && radial.length()<75);
            }
            System.out.println("Extra circuit: axis="+axis+" ticks="+tick+" angle="+total);
            assertTrue(total>=Math.PI*2,"stuck on axis "+axis+" at "+state.position());
        }
    }

    @Test void cannotWalkThroughThreeBlockWallOrJumpThroughCeiling() {
        PlanetDefinition distantCenter=new PlanetDefinition(0,-1000,0,64,1);
        AABB floor=new AABB(-100,-1,-100,100,0,100);
        AABB wall=new AABB(-2,0,4,2,3,5);
        var state=RadialPhysics.State.at(new Vec3(0.5,0,0.5),distantCenter);
        for(int tick=0;tick<200;tick++) state=RadialPhysics.tick(state,new RadialPhysics.Input(tick,1,0,false,false,false,0,0),distantCenter,bounds->List.of(floor,wall));
        assertTrue(state.position().z<3.71,"walked through wall");
        AABB roof=new AABB(-10,2.2,-10,10,3,10);
        state=RadialPhysics.State.at(new Vec3(0,0,0),distantCenter);
        for(int tick=0;tick<10;tick++) state=RadialPhysics.tick(state,RadialPhysics.Input.IDLE,distantCenter,bounds->List.of(floor,roof));
        double highest=0;
        for(int tick=0;tick<40;tick++) {
            state=RadialPhysics.tick(state,new RadialPhysics.Input(tick,0,0,tick==0,false,false,0,0),distantCenter,bounds->List.of(floor,roof));
            highest=Math.max(highest,state.position().y);
        }
        assertTrue(highest<=0.401,"jumped through ceiling: "+highest);
        assertTrue(state.grounded());
    }
    @Test void strafeMatchesActualMinecraftCameraLeftAndModelUp() {
        for (Vec3 up : List.of(new Vec3(0,1,0), new Vec3(1,0,0), new Vec3(0,-1,0), new Vec3(1,1,1).normalize())) {
            GravityFrame frame = GravityFrame.at(up);
            for (float yaw : new float[]{0,45,90,180,-90}) {
                var camera = new org.joml.Quaternionf().rotationYXZ((float)Math.PI - yaw * (float)Math.PI / 180, 0, 0);
                camera.premul(frame.rotation());
                var cameraLeft = new Vec3(new org.joml.Vector3f(-1,0,0).rotate(camera));
                assertTrue(frame.left(yaw).dot(cameraLeft) > 0.99999, "A must move toward camera left");
                assertTrue(frame.look(yaw,0).dot(new Vec3(new org.joml.Vector3f(0,0,-1).rotate(camera))) > 0.99999);
                var model = new org.joml.Quaternionf(frame.rotation()).rotateY((float)Math.toRadians(180-yaw));
                assertTrue(new Vec3(new org.joml.Vector3f(0,1,0).rotate(model)).dot(up)>0.99999, "model must remain radial upright");
            }
        }
    }

    @Test void largeRoundPlanetCompletesCircuit() {
        PlanetDefinition large = new PlanetDefinition(0,96,0,256,20261004);
        var state = RadialPhysics.State.at(center.add(.5,270,.5),large);
        RadialPhysics.Collider collider = bounds -> terrain(large,bounds);
        for(int t=0;t<160;t++) state=RadialPhysics.tick(state,RadialPhysics.Input.IDLE,large,collider);
        double last=Math.atan2(state.position().z,state.position().y-96),total=0;
        int t=0;
        for(;t<10000 && total<Math.PI*2;t++) {
            Vec3 direction=new Vec3(1,0,0).cross(state.frame().up()).normalize();
            float yaw=(float)Math.toDegrees(Math.atan2(-direction.dot(state.frame().east()),direction.dot(state.frame().north())));
            state=RadialPhysics.tick(state,new RadialPhysics.Input(t,1,0,false,true,false,yaw,0),large,collider);
            double angle=Math.atan2(state.position().z,state.position().y-96),delta=angle-last;
            if(delta>Math.PI)delta-=Math.PI*2;
            if(delta<-Math.PI)delta+=Math.PI*2;
            total+=delta;last=angle;
            assertTrue(state.position().distanceTo(center)>253 && state.position().distanceTo(center)<261);
        }
        System.out.println("Large circuit: ticks="+t+" radians="+total);
        assertTrue(total>=Math.PI*2,"large planet walk stuck");
    }
}
