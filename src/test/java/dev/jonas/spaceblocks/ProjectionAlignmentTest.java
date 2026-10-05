package dev.jonas.spaceblocks;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProjectionAlignmentTest {
  @Test
  void everySupportedSizeHasExactlyOppositeHalfMap() {
    for (int requested = 32; requested <= 1024; requested++) {
      var p = new Planet(requested);
      double r = p.projectionRadius();
      assertEquals(Math.PI, p.size() / 2.0 / r, 1e-12);
      var exit = PeriodicMath.project(p.size() / 2.0, 0, 0, r);
      assertEquals(0, exit.x(), 1e-10);
      assertEquals(-2 * r, exit.y(), 1e-10);
      var second = PeriodicMath.project(0, 0, p.size() / 2.0, r);
      assertEquals(0, second.z(), 1e-10);
      assertEquals(exit.y(), second.y(), 1e-10);
    }
  }

  @Test
  void compassAndTransitionAreContinuous() {
    assertEquals("N", HudGeometry.heading(180));
    assertEquals("N", HudGeometry.heading(-180));
    assertEquals("E", HudGeometry.heading(-90));
    assertEquals("S", HudGeometry.heading(0));
    assertEquals("W", HudGeometry.heading(90));
    assertEquals(0, HudGeometry.roll(0));
    assertEquals(180, HudGeometry.roll(.5));
    assertEquals(0, HudGeometry.roll(1));
    assertEquals(.02, HudGeometry.angleDelta(-Math.PI + .01, Math.PI - .01), 1e-12);
  }
}
