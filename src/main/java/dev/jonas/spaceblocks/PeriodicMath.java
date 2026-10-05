package dev.jonas.spaceblocks;

/** Algorithms adapted from Jeija/Spheretest, LGPL-2.1-or-later.
 * Exact origins: docs/SPHERETEST-SOURCES.md. Units: blocks and ticks.
 */
public final class PeriodicMath {
    private PeriodicMath() {}
    public record Point(double x, double y, double z) {}
    public record Offset(int x, int z) {}
    public static int circumference(int radius) { return (int)Math.ceil(radius / 16.0 * Math.PI) * 32; }
    public static int wrap(int value, int size) { return Math.floorMod(value + size / 2, size) - size / 2; }
    public static double wrap(double value, int size) { return value - size * Math.floor((value + size / 2.0) / size); }
    public static Offset nearest(double x,double z,double cameraX,double cameraZ,int size) {
        double best=Double.POSITIVE_INFINITY; Offset result=new Offset(0,0);
        for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++) {
            double dx=x+i*size-cameraX,dz=z+j*size-cameraZ,distance=dx*dx+dz*dz;
            if(distance<best){best=distance;result=new Offset(i*size,j*size);}
        }
        return result;
    }
    /** PLANET_KEEP_SCALE, relative to camera: R exp((dy + i distance)/R) - R. */
    public static Point project(double dx,double dy,double dz,double radius) {
        double horizontal=Math.hypot(dx,dz),angle=horizontal/radius,radial=radius*Math.exp(dy/radius);
        double factor=horizontal<1e-9?Math.exp(dy/radius):radial*Math.sin(angle)/horizontal;
        return new Point(dx*factor,radial*Math.cos(angle)-radius,dz*factor);
    }
    public static Point unproject(double x,double y,double z,double radius) {
        double horizontal=Math.hypot(x,z),radial=Math.hypot(horizontal,y+radius),distance=radius*Math.atan2(horizontal,y+radius);
        double factor=horizontal<1e-9?1:distance/horizontal;
        return new Point(x*factor,radius*Math.log(radial/radius),z*factor);
    }
    public static double gravityCoefficient(double altitude,double radius){return altitude<0?Math.exp(altitude/radius):Math.exp(-2*altitude/radius);}
    /** d973e4b: use flat speed; no visual block-width multiplier. */
    public static double centrifugal(double vx,double vz,double altitude,double radius){return 2*(vx*vx+vz*vz)/(radius*Math.exp(altitude/radius));}
}
