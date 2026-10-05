package dev.jonas.spaceblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A local view of the existing bottom connection; source blocks remain in canonical storage. */
public final class BottomPassage {
  public static final int VIEW = 16;

  private BottomPassage() {}

  public static boolean visible(Level level, double eyeY) {
    var p = Planet.of(level);
    return p != null
        && PlanetSettings.get(level).fallthrough
        && Math.abs(eyeY - p.bottom()) <= VIEW;
  }

  /** Continuous coordinates for the opposite side, reflected through the bottom plane. */
  public static Vec3 reflect(Planet p, Vec3 point) {
    return new Vec3(
        PeriodicMath.wrap(point.x + p.size() / 2.0, p.size()),
        2.0 * p.bottom() - point.y,
        PeriodicMath.wrap(point.z, p.size()));
  }

  public static BlockPos reflectedBlock(Planet p, BlockPos point) {
    return new BlockPos(
        PeriodicMath.wrap(point.getX() + p.size() / 2, p.size()),
        2 * p.bottom() - 1 - point.getY(),
        PeriodicMath.wrap(point.getZ(), p.size()));
  }

  /** Select the representation close to the observer, including the bottom only when enabled. */
  public static Vec3 nearest(Level level, Vec3 eye, Vec3 point) {
    var p = Planet.of(level);
    if (p == null) return point;
    var direct = eye.add(p.delta(eye, point));
    if (!visible(level, eye.y) || point.y < p.bottom()) return direct;
    var other = reflect(p, point);
    other = eye.add(p.delta(eye, other));
    return other.distanceToSqr(eye) < direct.distanceToSqr(eye) ? other : direct;
  }
}
