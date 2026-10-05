package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.surface.*;
import dev.jonas.spaceblocks.physics.GravityFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/** CPU counterpart of flat_terrain.vsh, used for controls and selection near corrected corners. */
public final class FlatVisual {
    public record View(FlatDefinition definition,Vec3 eye,GravityFrame frame,float corner) {
        public Vec3 project(Vec3 world) {
            Vec3 planar=world.subtract(eye);
            var p=definition.locate(world.x,world.z);var anchor=definition.locate(eye.x,eye.z);
            if(p==null||anchor==null)return planar;
            Vec3 globe=CubeTopology.sphere(p,definition.faceSize(),definition.radius(),world.y-64)
                .subtract(CubeTopology.sphere(anchor,definition.faceSize(),definition.radius(),eye.y-64));
            Vec3 curved=new Vec3(globe.dot(frame.east()),globe.dot(frame.up()),globe.dot(frame.north()));
            double weight=Math.max(corner,Math.max(FlatShaders.morph(eye.y)*FlatShaders.smooth(8,24,planar.length()),FlatShaders.smooth(64,192,Math.hypot(planar.x,planar.z))));
            return planar.lerp(curved,weight);
        }
        public Matrix3d jacobian(Vec3 world) {
            double h=.01;Vec3 origin=project(world);
            Vec3 x=project(world.add(h,0,0)).subtract(origin).scale(1/h);
            Vec3 y=project(world.add(0,h,0)).subtract(origin).scale(1/h);
            Vec3 z=project(world.add(0,0,h)).subtract(origin).scale(1/h);
            return new Matrix3d(x.x,x.y,x.z,y.x,y.y,y.z,z.x,z.y,z.z);
        }
        public Vec3 unproject(Vec3 target,Vec3 guess) {
            Vec3 point=guess;
            for(int i=0;i<8;i++) {
                Vec3 error=target.subtract(project(point));if(error.lengthSqr()<1e-8)break;
                Matrix3d j=jacobian(point);if(Math.abs(j.determinant())<.00001)return guess;
                Vector3d step=j.invert().transform(new Vector3d(error.x,error.y,error.z));
                if(!Double.isFinite(step.length())||step.length()>8)return guess;
                point=point.add(step.x,step.y,step.z);
            }
            return point;
        }
    }
    public static View view(Vec3 eye) {
        var d=FlatClient.surface;var p=d.locate(eye.x,eye.z);FlatClient.updateFrame(p);
        return new View(d,eye,FlatClient.frame,FlatShaders.corner(p));
    }
    public static Vec3 movement(Vec3 input,Entity player) {
        if(!FlatClient.active()||player!=Minecraft.getInstance().player)return input;
        View view=view(player.getEyePosition());if(view.corner<.001)return input;
        double angle=Math.toRadians(player.getYRot()),cos=Math.cos(angle),sin=Math.sin(angle);
        double x=input.x*cos-input.z*sin,z=input.z*cos+input.x*sin;
        Matrix3d j=view.jacobian(view.eye);double determinant=j.m00()*j.m22()-j.m20()*j.m02();
        if(Math.abs(determinant)<.00001)return input;
        double px=(x*j.m22()-z*j.m20())/determinant,pz=(z*j.m00()-x*j.m02())/determinant;
        double length=Math.hypot(px,pz),original=Math.hypot(x,z);if(length<1e-8)return input;
        px*=original/length;pz*=original/length;
        return new Vec3(px*cos+pz*sin,input.y,-px*sin+pz*cos);
    }
    public static HitResult pick(Entity player,double distance,float partial,boolean fluids) {
        View view=view(player.getEyePosition(partial));Vec3 direction=player.getViewVector(partial),previous=view.eye;
        for(double t=.1;t<=distance+.099;t+=.1) {
            Vec3 target=direction.scale(Math.min(t,distance)),point=view.unproject(target,previous.add(direction.scale(.1)));
            if(point.distanceTo(view.eye)>distance)point=view.eye.add(point.subtract(view.eye).normalize().scale(distance));
            var hit=player.level().clip(new ClipContext(previous,point,ClipContext.Block.OUTLINE,fluids?ClipContext.Fluid.ANY:ClipContext.Fluid.NONE,player));
            if(hit.getType()!=HitResult.Type.MISS)return hit;
            previous=point;
        }
        return BlockHitResult.miss(previous,net.minecraft.core.Direction.getNearest(direction.x,direction.y,direction.z),net.minecraft.core.BlockPos.containing(previous));
    }
}
