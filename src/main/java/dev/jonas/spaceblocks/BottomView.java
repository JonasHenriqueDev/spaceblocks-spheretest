package dev.jonas.spaceblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Visibility queries use existing chunks only; interaction reach remains independently limited. */
public final class BottomView {
  private BottomView() {}

  public static double rayDistance(double height, double radius, Vec3 direction) {
    if (height < 0 || direction.y >= 0) return Double.NaN;
    double inner = radius * Math.exp(-height / radius);
    double along = -radius * direction.y;
    double discriminant = along * along - radius * radius + inner * inner;
    return discriminant < 0 ? Double.NaN : along - Math.sqrt(discriminant);
  }

  public static boolean looking(Level level, Entity entity, Vec3 eye, Vec3 forward, int chunks) {
    return looking(level, entity, eye, forward, chunks, true);
  }

  public static boolean looking(
      Level level, Entity entity, Vec3 eye, Vec3 forward, int chunks, boolean projected) {
    var p = Planet.of(level);
    if (p == null || !PlanetSettings.get(level).fallthrough || chunks < 1) return false;
    double height = eye.y - p.bottom(), budget = chunks * 16.0;
    if (height < 0 || height > budget || forward.y > .3) return false;
    var direction = forward.normalize();
    var right = direction.cross(new Vec3(0, 1, 0));
    if (right.lengthSqr() < .001) right = new Vec3(1, 0, 0);
    right = right.normalize();
    var up = right.cross(direction).normalize();
    for (var ray :
        new Vec3[] {
          direction,
          direction.add(right.scale(.3)).normalize(),
          direction.add(right.scale(-.3)).normalize(),
          direction.add(up.scale(.3)).normalize(),
          direction.add(up.scale(-.3)).normalize()
        }) {
      double t =
          projected
              ? rayDistance(height, p.projectionRadius(), ray)
              : (ray.y < 0 ? height / -ray.y : Double.NaN);
      if (!Double.isFinite(t) || t < 0) continue;
      var v = ray.scale(t);
      var q =
          projected
              ? PeriodicMath.unproject(v.x, v.y, v.z, p.projectionRadius())
              : new PeriodicMath.Point(v.x, v.y, v.z);
      if (!Double.isFinite(q.y()) || new Vec3(q.x(), q.y(), q.z()).length() > budget) continue;
      Vec3 previous = eye;
      boolean clear = true;
      for (int i = 1; i <= 16; i++) {
        var step = ray.scale(t * i / 16.0);
        var flat =
            projected
                ? PeriodicMath.unproject(step.x, step.y, step.z, p.projectionRadius())
                : new PeriodicMath.Point(step.x, step.y, step.z);
        var next = eye.add(flat.x(), flat.y(), flat.z());
        // Stop just above the seam; the opposite floor must remain visible/minable when closed.
        if (i == 16) next = new Vec3(next.x, p.bottom() + .02, next.z);
        var block = p.canonical(BlockPos.containing(next));
        if (level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4) == null
            || level
                    .clip(
                        new ClipContext(
                            previous,
                            next,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            entity))
                    .getType()
                != HitResult.Type.MISS) {
          clear = false;
          break;
        }
        previous = next;
      }
      if (clear) return true;
    }
    return false;
  }
}
