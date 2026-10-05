package dev.jonas.spaceblocks.surface;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

/** Affine camera charts for invisible edge portals. All transforms preserve vertical gravity. */
public final class PortalTopology {
    public record View(Face face,double xx,double xz,double zx,double zz,double tx,double tz,int depth) {
        public Vec3 project(double u,double y,double v){return new Vec3(xx*u+xz*v+tx,y,zx*u+zz*v+tz);}
        public Position camera(double u,double v){double x=u-tx,z=v-tz;return new Position(face,xx*x+zx*z,xz*x+zz*z);}
        public String key(){return face+":"+xx+":"+xz+":"+zx+":"+zz+":"+tx+":"+tz;}
    }
    public record Chunk(Face face,int x,int z,double distance) {}
    public static List<View> views(Position eye,int size,double range) {
        List<View> result=new ArrayList<>();ArrayDeque<View> queue=new ArrayDeque<>();Set<String> seen=new HashSet<>();
        queue.add(new View(eye.face(),1,0,0,1,0,0,0));
        while(!queue.isEmpty()&&result.size()<80) {
            View v=queue.removeFirst();if(!seen.add(v.key()))continue;
            Position camera=v.camera(eye.u(),eye.v());
            double dx=Math.max(0,Math.max(-camera.u(),camera.u()-size)),dz=Math.max(0,Math.max(-camera.v(),camera.v()-size));
            if(Math.hypot(dx,dz)>range)continue;
            result.add(v);if(v.depth>=5)continue;
            for(Edge edge:Edge.values()) {
                var base=cross(new Position(v.face,0,0),Vec3.ZERO,0,size,edge).position();
                var ex=cross(new Position(v.face,0,0),new Vec3(1,0,0),0,size,edge).velocity();
                var ez=cross(new Position(v.face,0,0),new Vec3(0,0,1),0,size,edge).velocity();
                // Invert parent->child rotation and compose child->parent->root.
                double a=ex.x,b=ex.z,c=ez.x,e=ez.z;
                double x=-a*base.u()-b*base.v(),z=-c*base.u()-e*base.v();
                queue.add(new View(base.face(),v.xx*a+v.xz*c,v.xx*b+v.xz*e,v.zx*a+v.zz*c,v.zx*b+v.zz*e,v.xx*x+v.xz*z+v.tx,v.zx*x+v.zz*z+v.tz,v.depth+1));
            }
        }
        result.sort(Comparator.comparingInt(View::depth));return result;
    }
    public static List<Chunk> chunks(FlatDefinition d,Position eye,double range) {
        Map<Long,Chunk> unique=new HashMap<>();int size=d.faceSize();
        for(View v:views(eye,size,range)) {
            Position c=v.camera(eye.u(),eye.v());
            int minX=Math.max(0,(int)Math.floor((c.u()-range)/16)),maxX=Math.min(size/16-1,(int)Math.floor((c.u()+range)/16));
            int minZ=Math.max(0,(int)Math.floor((c.v()-range)/16)),maxZ=Math.min(size/16-1,(int)Math.floor((c.v()+range)/16));
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
                var center=v.project(x*16+8,0,z*16+8);double distance=Math.hypot(center.x-eye.u(),center.z-eye.v());if(distance>range+12)continue;
                int cx=((int)d.originX(v.face)>>4)+x,cz=((int)d.originZ(v.face)>>4)+z;
                long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);var chunk=new Chunk(v.face,cx,cz,distance);
                unique.merge(key,chunk,(a,b)->a.distance<b.distance?a:b);
            }
        }
        var list=new ArrayList<>(unique.values());list.sort(Comparator.comparingDouble(Chunk::distance));return list;
    }
}
