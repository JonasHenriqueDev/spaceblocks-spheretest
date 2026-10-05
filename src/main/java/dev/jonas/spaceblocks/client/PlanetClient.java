package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class PlanetClient {
  public record Audio(Planet planet, Vec3 eye) {}

  public static volatile Audio audio;
  public static Vec3 beforeTeleport;

  public static void restoreVelocity(PlanetNetwork.Velocity packet) {
    var player = Minecraft.getInstance().player;
    if (player == null) return;
    var v = beforeTeleport;
    beforeTeleport = null;
    player.setDeltaMovement(
        v == null
            ? new Vec3(packet.x(), packet.y(), packet.z())
            : new Vec3(v.x, packet.bounce() ? -v.y : v.y, v.z));
  }

  public static Planet planet() {
    return Planet.of(Minecraft.getInstance().level);
  }

  public static boolean active() {
    return planet() != null;
  }

  public static HitResult pickScene(
      Entity entity, double blockReach, double entityReach, float partial) {
    var eye = entity.getEyePosition(partial);
    var direction = entity.getViewVector(partial);
    Vec3 previous = eye;
    var d = planet();
    double reach = Math.max(blockReach, entityReach);
    for (double t = .05; t <= reach + .049; t += .05) {
      var v = direction.scale(Math.min(t, reach));
      var p = PeriodicMath.unproject(v.x, v.y, v.z, d.radius());
      var point = eye.add(p.x(), p.y(), p.z());
      var block =
          entity
              .level()
              .clip(
                  new ClipContext(
                      previous, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
      double best =
          block.getType() == HitResult.Type.MISS
              ? Double.POSITIVE_INFINITY
              : block.getLocation().distanceToSqr(previous);
      EntityHitResult selected = null;
      for (var target :
          entity
              .level()
              .getEntities(
                  entity,
                  new AABB(previous, point).inflate(1),
                  e -> !e.isSpectator() && e.isPickable())) {
        var box = target.getBoundingBox().inflate(target.getPickRadius());
        var center = box.getCenter();
        var delta = d.delta(eye, center);
        box = box.move(eye.x + delta.x - center.x, 0, eye.z + delta.z - center.z);
        var intersection = box.clip(previous, point);
        if (intersection.isPresent()) {
          var hit = intersection.get();
          double distance = hit.distanceToSqr(previous);
          if (distance < best && hit.distanceToSqr(eye) <= entityReach * entityReach) {
            best = distance;
            selected = new EntityHitResult(target, hit);
          }
        }
      }
      if (selected != null) return selected;
      if (block.getType() != HitResult.Type.MISS
          && block.getLocation().distanceToSqr(eye) <= blockReach * blockReach) return block;
      previous = point;
    }
    return BlockHitResult.miss(
        previous,
        Direction.getNearest(direction.x, direction.y, direction.z),
        net.minecraft.core.BlockPos.containing(previous));
  }

  public static Vec3 project(Vec3 world, Vec3 eye) {
    var d = planet();
    var p = PeriodicMath.project(world.x - eye.x, world.y - eye.y, world.z - eye.z, d.radius());
    return new Vec3(p.x(), p.y(), p.z());
  }

  public static HitResult pick(Entity entity, double reach, float partial, boolean fluid) {
    var eye = entity.getEyePosition(partial);
    var direction = entity.getViewVector(partial);
    Vec3 previous = eye;
    var d = planet();
    for (double t = .1; t <= reach + .099; t += .1) {
      var v = direction.scale(Math.min(t, reach));
      var p = PeriodicMath.unproject(v.x, v.y, v.z, d.radius());
      var point = eye.add(p.x(), p.y(), p.z());
      if (point.distanceToSqr(eye) > reach * reach)
        point = eye.add(point.subtract(eye).normalize().scale(reach));
      var hit =
          entity
              .level()
              .clip(
                  new ClipContext(
                      previous,
                      point,
                      ClipContext.Block.OUTLINE,
                      fluid ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                      entity));
      if (hit.getType() != HitResult.Type.MISS) return hit;
      previous = point;
    }
    return BlockHitResult.miss(
        previous,
        Direction.getNearest(direction.x, direction.y, direction.z),
        net.minecraft.core.BlockPos.containing(previous));
  }
}
