package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.PeriodicMath;

/** Local diagnostic toggle, reset to on at each game launch. World mechanics are unaffected. */
public final class PlanetProjection {
  public static boolean enabled = true;

  private PlanetProjection() {}

  public static PeriodicMath.Point project(double x, double y, double z, double radius) {
    return enabled ? PeriodicMath.project(x, y, z, radius) : new PeriodicMath.Point(x, y, z);
  }

  public static PeriodicMath.Point unproject(double x, double y, double z, double radius) {
    return enabled ? PeriodicMath.unproject(x, y, z, radius) : new PeriodicMath.Point(x, y, z);
  }
}
