package dev.jonas.spaceblocks;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BottomPassageTest {
  @Test
  void oppositeConnectionIsAnInvolutionAcrossEdgesAndDepths() {
    for (var p : new Planet[] {Planet.SMALL, Planet.LARGE, Planet.NATURAL, Planet.LAB})
      for (double x : new double[] {-p.size() / 2.0, -1.5, .5, p.size() / 2.0 - 1}) {
        var a = new Vec3(x, p.bottom() - 3.25, 10.5);
        var b = BottomPassage.reflect(p, a);
        assertEquals(p.bottom() + 3.25, b.y);
        assertEquals(p.size() / 2.0, Math.abs(PeriodicMath.wrap(b.x - a.x, p.size())));
        assertEquals(a, BottomPassage.reflect(p, b));
      }
  }

  @Test
  void voxelCentersAndContinuousReflectionAgree() {
    var p = Planet.SMALL;
    for (int y = p.bottom() - 6; y <= p.bottom() + 6; y++) {
      var a = new BlockPos(40, y, 30);
      var b = BottomPassage.reflectedBlock(p, a);
      assertEquals(Vec3.atCenterOf(b), BottomPassage.reflect(p, Vec3.atCenterOf(a)));
      assertEquals(a, BottomPassage.reflectedBlock(p, b));
    }
  }
}
