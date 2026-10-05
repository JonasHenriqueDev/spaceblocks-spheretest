package dev.jonas.spaceblocks.surface;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** A cube net with six square storage charts. All transport is in a flat X/Z plane. */
public final class CubeTopology {
    private CubeTopology() {}
    public enum Face {
        FRONT(1,1,new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,-1,0)),
        RIGHT(2,1,new Vec3(1,0,0),new Vec3(0,0,-1),new Vec3(0,-1,0)),
        BACK(3,1,new Vec3(0,0,-1),new Vec3(-1,0,0),new Vec3(0,-1,0)),
        LEFT(0,1,new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,-1,0)),
        NORTH(1,0,new Vec3(0,1,0),new Vec3(1,0,0),new Vec3(0,0,1)),
        SOUTH(1,2,new Vec3(0,-1,0),new Vec3(1,0,0),new Vec3(0,0,-1));
        public final int column, row;
        public final Vec3 normal, uAxis, vAxis;
        Face(int column,int row,Vec3 normal,Vec3 uAxis,Vec3 vAxis) {
            this.column=column;this.row=row;this.normal=normal;this.uAxis=uAxis;this.vAxis=vAxis;
        }
        static Face facing(Vec3 normal) {
            for(Face face:values()) if(face.normal.dot(normal)>.999) return face;
            throw new IllegalArgumentException("Not a cube face normal: "+normal);
        }
    }
    public enum Edge { WEST, EAST, NORTH, SOUTH }
    /** Coordinates in blocks, with u/v in [0,size). */
    public record Position(Face face,double u,double v) {
        public Vec3 cube(double size) {
            double h=size*.5;
            return face.normal.scale(h).add(face.uAxis.scale(u-h)).add(face.vAxis.scale(v-h));
        }
    }
    public record Transition(Position position,Vec3 velocity,float yaw,int crossings) {}

    /** Constant quarter-turn transport on one edge; no radial gravity or cube deformation. */
    public static Transition cross(Position position,Vec3 velocity,float yaw,double size,Edge edge) {
        Face old=position.face();
        double h=size*.5;
        boolean alongU=edge==Edge.EAST || edge==Edge.WEST;
        int sign=edge==Edge.EAST || edge==Edge.SOUTH?1:-1;
        Vec3 outward=(alongU?old.uAxis:old.vAxis).scale(sign);
        Face next=Face.facing(outward);
        double across=alongU?position.u()-h:position.v()-h;
        double overflow=sign*across-h;
        double parallel=alongU?position.v()-h:position.u()-h;
        Vec3 parallelAxis=alongU?old.vAxis:old.uAxis;
        Vec3 folded=next.normal.scale(h).add(old.normal.scale(h-overflow)).add(parallelAxis.scale(parallel));
        Position result=new Position(next,h+folded.dot(next.uAxis),h+folded.dot(next.vAxis));
        Vec3 tangent=old.uAxis.scale(velocity.x).add(old.vAxis.scale(velocity.z));
        Vec3 rotated=foldVector(tangent,outward,old.normal);
        Vec3 newVelocity=new Vec3(rotated.dot(next.uAxis),velocity.y,rotated.dot(next.vAxis));
        double a=Math.toRadians(yaw);
        Vec3 look=old.uAxis.scale(-Math.sin(a)).add(old.vAxis.scale(Math.cos(a)));
        Vec3 newLook=foldVector(look,outward,old.normal);
        float newYaw=(float)Math.toDegrees(Math.atan2(-newLook.dot(next.uAxis),newLook.dot(next.vAxis)));
        return new Transition(result,newVelocity,newYaw,1);
    }
    private static Vec3 foldVector(Vec3 vector,Vec3 edgeNormal,Vec3 faceNormal) {
        double component=vector.dot(edgeNormal);
        return vector.subtract(edgeNormal.scale(component)).subtract(faceNormal.scale(component));
    }
    public static Transition wrap(Position position,Vec3 velocity,float yaw,double size) {
        if(!Double.isFinite(size) || size<=0 || !Double.isFinite(position.u()) || !Double.isFinite(position.v()))
            throw new IllegalArgumentException("Nonfinite coordinates or invalid chart size");
        int count=0;
        while(position.u()<0 || position.u()>=size || position.v()<0 || position.v()>=size) {
            if(count++>=256) throw new IllegalArgumentException("Chart transport exceeds 256 edges");
            double du=Math.max(-position.u(),position.u()-size),dv=Math.max(-position.v(),position.v()-size);
            Edge edge;
            if(du>=dv && (position.u()<0 || position.u()>=size)) edge=position.u()<0?Edge.WEST:Edge.EAST;
            else edge=position.v()<0?Edge.NORTH:Edge.SOUTH;
            var step=cross(position,velocity,yaw,size,edge);
            position=step.position();velocity=step.velocity();yaw=step.yaw();
            // A half-open chart must not immediately send an exactly-on-edge point back.
            if(position.u()==size) position=new Position(position.face(),size-1e-7,position.v());
            if(position.v()==size) position=new Position(position.face(),position.u(),size-1e-7);
        }
        return new Transition(position,velocity,yaw,count);
    }
    public static Position canonical(Position position,double size) { return wrap(position,Vec3.ZERO,0,size).position(); }

    /** Shared relaxed cube-to-sphere projection. This only supplies visual coordinates. */
    public static Vec3 sphere(Position position,double size,double radius,double height) {
        return SphereProjection.sample(position,size).scale(radius+height);
    }
    public static List<Edge> edges() { return List.of(Edge.values()); }
}
