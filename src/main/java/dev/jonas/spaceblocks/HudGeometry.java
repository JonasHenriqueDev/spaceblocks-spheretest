package dev.jonas.spaceblocks;

/** Pure presentation helpers; compass directions refer to the periodic plane. */
public final class HudGeometry {
  private HudGeometry() {}

  public static String heading(float yaw) {
    return new String[] {"S", "SW", "W", "NW", "N", "NE", "E", "SE"}
        [Math.floorMod((int) Math.floor(yaw / 45.0 + .5), 8)];
  }

  public static double angleDelta(double target, double current) {
    return target
        - current
        - 2 * Math.PI * Math.floor((target - current + Math.PI) / (2 * Math.PI));
  }

  /** Smooth inversion cue returning to vanilla upright; not a physical change of gravity. */
  public static float roll(double progress) {
    if (progress <= 0 || progress >= 1) return 0;
    double t = progress < .5 ? progress * 2 : (1 - progress) * 2;
    return (float) (180 * t * t * (3 - 2 * t));
  }
}
