package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import java.util.Iterator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The unused storage below the bottom must not form an invisible bedrock floor. */
@Mixin(CollisionGetter.class)
public interface PeriodicBottomCollisionMixin {
  @Inject(method = "getBlockCollisions", at = @At("RETURN"), cancellable = true)
  private void bottom(Entity entity, AABB box, CallbackInfoReturnable<Iterable<VoxelShape>> ci) {
    if (!((Object) this instanceof Level level)) return;
    var p = Planet.of(level);
    if (p == null || !PlanetSettings.get(level).fallthrough || box.minY >= p.bottom()) return;
    var original = ci.getReturnValue();
    boolean blocked = false;
    if (entity instanceof Player
        && !entity.isSpectator()
        && Math.abs(entity.getY() - p.bottom()) < BottomPassage.VIEW) {
      double x = PeriodicMath.wrap(entity.getX() + p.size() / 2.0, p.size());
      var exit = entity.getBoundingBox().move(x - entity.getX(), p.bottom() + 1 - entity.getY(), 0);
      // This query is entirely above the bottom, so it cannot recurse into the floor query.
      blocked = !level.noCollision(entity, exit);
    }
    final boolean floor = blocked;
    ci.setReturnValue(
        () ->
            new Iterator<>() {
              final Iterator<VoxelShape> input = original.iterator();
              VoxelShape next;
              boolean floorPending = floor;

              public boolean hasNext() {
                while (next == null && input.hasNext()) {
                  var shape = input.next();
                  if (shape.isEmpty()
                      || shape.max(net.minecraft.core.Direction.Axis.Y) <= p.bottom()) continue;
                  if (shape.min(net.minecraft.core.Direction.Axis.Y) < p.bottom())
                    shape =
                        Shapes.joinUnoptimized(
                            shape,
                            Shapes.create(
                                new AABB(
                                    -30_000_000,
                                    p.bottom(),
                                    -30_000_000,
                                    30_000_000,
                                    1_000_000,
                                    30_000_000)),
                            BooleanOp.AND);
                  if (!shape.isEmpty()) next = shape;
                }
                if (next == null && floorPending) {
                  floorPending = false;
                  next =
                      Shapes.create(
                          new AABB(
                              box.minX - 1,
                              p.bottom() - 1,
                              box.minZ - 1,
                              box.maxX + 1,
                              p.bottom(),
                              box.maxZ + 1));
                }
                return next != null;
              }

              public VoxelShape next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                var result = next;
                next = null;
                return result;
              }
            });
  }
}
