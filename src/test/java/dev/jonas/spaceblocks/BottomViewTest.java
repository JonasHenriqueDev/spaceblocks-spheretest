package dev.jonas.spaceblocks;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BottomViewTest {
  @Test
  void bottomRayMatchesExponentialAndRejectsWrongDirection() {
    for (int requested : new int[] {32, 64, 128, 256, 1024}) {
      double r = new Planet(requested).projectionRadius();
      for (double height : new double[] {1, 17.6, 48, 128, 512}) {
        double t = BottomView.rayDistance(height, r, new Vec3(0, -1, 0));
        assertEquals(r * (1 - Math.exp(-height / r)), t, 1e-8);
        if (height / r < 20) assertEquals(-height, PeriodicMath.unproject(0, -t, 0, r).y(), 1e-6);
      }
      assertTrue(Double.isNaN(BottomView.rayDistance(20, r, new Vec3(0, 1, 0))));
      assertTrue(Double.isNaN(BottomView.rayDistance(20, r, new Vec3(1, 0, 0))));
    }
  }
}
