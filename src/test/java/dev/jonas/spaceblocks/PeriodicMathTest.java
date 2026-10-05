package dev.jonas.spaceblocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
class PeriodicMathTest {
    @Test void mapSizeMatchesOriginalChunkRounding(){assertEquals(224,PeriodicMath.circumference(32));assertEquals(1632,PeriodicMath.circumference(256));}
    @Test void fourEdgesAndDiagonal(){int s=224;assertEquals(-111.75,PeriodicMath.wrap(112.25,s));assertEquals(111.75,PeriodicMath.wrap(-112.25,s));assertEquals(-112,PeriodicMath.wrap(112,s));assertEquals(111,PeriodicMath.wrap(-113,s));for(int i=-3;i<=3;i++)for(int j=-3;j<=3;j++){assertEquals(35.25,PeriodicMath.wrap(35.25+i*s,s));assertEquals(-67.5,PeriodicMath.wrap(-67.5+j*s,s));}}
    @Test void fullLapsAndNearestNine(){for(int size:new int[]{224,1632}){double x=0,z=0;for(int i=0;i<size*4;i++){x=PeriodicMath.wrap(x+.25,size);z=PeriodicMath.wrap(z-.25,size);}assertEquals(0,x);assertEquals(0,z);var o=PeriodicMath.nearest(-size/2.,-size/2.,size/2.-1,size/2.-1,size);assertEquals(size,o.x());assertEquals(size,o.z());}}
    @Test void shaderGoldenCases(){var p=PeriodicMath.project(32*Math.PI/2,0,0,32);assertEquals(32,p.x(),1e-10);assertEquals(-32,p.y(),1e-10);var up=PeriodicMath.project(0,32*Math.log(2),0,32);assertEquals(32,up.y(),1e-10);var down=PeriodicMath.project(0,-32*Math.log(2),0,32);assertEquals(-16,down.y(),1e-10);}
    @Test void inverseAndCameraRelativeCoordinates(){var random=new Random(4732);for(int i=0;i<10000;i++){double x=random.nextDouble(-20,20),y=random.nextDouble(-20,20),z=random.nextDouble(-20,20);var p=PeriodicMath.project(x,y,z,32);var q=PeriodicMath.unproject(p.x(),p.y(),p.z(),32);assertEquals(x,q.x(),1e-10);assertEquals(y,q.y(),1e-10);assertEquals(z,q.z(),1e-10);}}
    @Test void conformalVerticalSliceAtTntHeights(){for(double h:new double[]{-24,0,32,128}){double epsilon=1e-5;var a=PeriodicMath.project(20,h,0,32);var x=PeriodicMath.project(20+epsilon,h,0,32);var y=PeriodicMath.project(20,h+epsilon,0,32);double horizontal=Math.hypot(x.x()-a.x(),x.y()-a.y())/epsilon,vertical=Math.hypot(y.x()-a.x(),y.y()-a.y())/epsilon;assertEquals(horizontal,vertical,vertical*1e-6);}}
    @Test void finalPhysicsDoesNotScaleSpeed(){assertEquals(1,PeriodicMath.gravityCoefficient(0,32));assertEquals(Math.exp(-1),PeriodicMath.gravityCoefficient(-32,32));assertEquals(Math.exp(-2),PeriodicMath.gravityCoefficient(32,32));assertEquals(2*(.3*.3+.4*.4)/32,PeriodicMath.centrifugal(.3,.4,0,32));assertEquals(2*(.3*.3+.4*.4)/(32*Math.E),PeriodicMath.centrifugal(.3,.4,32,32));}
}
