package dev.jonas.spaceblocks.surface;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

/** A relaxed, shared-vertex sphere map: edge derivatives approach each other instead of shearing. */
public final class SphereProjection {
    public static final int GRID=64, WIDTH=GRID+1;
    private static final float[][] MAP=build();
    private SphereProjection() {}
    public static float[] faceData(Face face) { return MAP[face.ordinal()]; }
    private static float[][] build() {
        Map<Long,Integer> ids=new HashMap<>();List<Vec3> nodes=new ArrayList<>();
        int[][] faces=new int[6][WIDTH*WIDTH];
        for(Face face:Face.values())for(int v=0;v<=GRID;v++)for(int u=0;u<=GRID;u++) {
            Vec3 c=face.normal.scale(GRID).add(face.uAxis.scale(2*u-GRID)).add(face.vAxis.scale(2*v-GRID));
            long key=((long)((int)c.x+GRID)<<32)|((long)((int)c.y+GRID)<<16)|((int)c.z+GRID);
            Integer id=ids.get(key);
            if(id==null){id=nodes.size();ids.put(key,id);nodes.add(initial(c.scale(1.0/GRID)));}
            faces[face.ordinal()][v*WIDTH+u]=id;
        }
        int n=nodes.size();int[] count=new int[n];int[][] neighbors=new int[n][4];
        for(int[] face:faces)for(int v=0;v<=GRID;v++)for(int u=0;u<=GRID;u++) {
            int index=v*WIDTH+u,id=face[index];
            if(u>0)link(neighbors,count,id,face[index-1]);if(u<GRID)link(neighbors,count,id,face[index+1]);
            if(v>0)link(neighbors,count,id,face[index-WIDTH]);if(v<GRID)link(neighbors,count,id,face[index+WIDTH]);
        }
        double[] previous=new double[n*3],next=new double[n*3];
        for(int i=0;i<n;i++){Vec3 v=nodes.get(i);previous[i*3]=v.x;previous[i*3+1]=v.y;previous[i*3+2]=v.z;}
        for(int iteration=0;iteration<1800;iteration++) {
            for(int i=0;i<n;i++) {
                double x=0,y=0,z=0;
                for(int j=0;j<count[i];j++){int offset=neighbors[i][j]*3;x+=previous[offset];y+=previous[offset+1];z+=previous[offset+2];}
                double inverse=1/Math.sqrt(x*x+y*y+z*z);next[i*3]=x*inverse;next[i*3+1]=y*inverse;next[i*3+2]=z*inverse;
            }
            double[] swap=previous;previous=next;next=swap;
        }
        float[][] map=new float[6][WIDTH*WIDTH*3];
        for(int face=0;face<6;face++)for(int i=0;i<faces[face].length;i++)for(int c=0;c<3;c++)map[face][i*3+c]=(float)previous[faces[face][i]*3+c];
        return map;
    }
    private static void link(int[][] neighbors,int[] count,int a,int b) {
        for(int i=0;i<count[a];i++)if(neighbors[a][i]==b)return;
        if(count[a]>=4)throw new IllegalStateException("Unexpected cube-net vertex degree");neighbors[a][count[a]++]=b;
    }
    private static Vec3 initial(Vec3 c) {
        double x=c.x,y=c.y,z=c.z;
        return new Vec3(x*Math.sqrt(1-y*y/2-z*z/2+y*y*z*z/3),y*Math.sqrt(1-z*z/2-x*x/2+z*z*x*x/3),z*Math.sqrt(1-x*x/2-y*y/2+x*x*y*y/3)).normalize();
    }
    public static Vec3 sample(Position position,double size) {
        Position p=canonical(position,size);double x=Math.min(GRID,p.u()*GRID/size),z=Math.min(GRID,p.v()*GRID/size);
        int u=Math.min(GRID-1,(int)x),v=Math.min(GRID-1,(int)z);double a=x-u,b=z-v;
        float[] map=MAP[p.face().ordinal()];double[] result=new double[3];
        for(int component=0;component<3;component++) {
            int i=(v*WIDTH+u)*3+component;
            result[component]=(map[i]*(1-a)+map[i+3]*a)*(1-b)+(map[i+WIDTH*3]*(1-a)+map[i+WIDTH*3+3]*a)*b;
        }
        return new Vec3(result[0],result[1],result[2]).normalize();
    }
}
