package dev.jonas.spaceblocks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.jonas.spaceblocks.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Explosion.class)
abstract class PeriodicExplosionMixin {
  @Shadow @Final private Level level;
  @Shadow @Final private double x;
  @Shadow @Final private double z;

  @ModifyExpressionValue(
      method = "explode",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
  private double closestX(double value) {
    var d = Planet.of(level);
    return d == null ? value : x + PeriodicMath.wrap(value - x, d.size());
  }

  @ModifyExpressionValue(
      method = "explode",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
  private double closestZ(double value) {
    var d = Planet.of(level);
    return d == null ? value : z + PeriodicMath.wrap(value - z, d.size());
  }

  @ModifyExpressionValue(
      method = "getSeenPercent",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
  private static AABB lineOfSight(AABB box, Vec3 origin, Entity entity) {
    var d = Planet.of(entity.level());
    if (d == null) return box;
    var c = box.getCenter();
    var delta = d.delta(origin, c);
    return box.move(origin.x + delta.x - c.x, 0, origin.z + delta.z - c.z);
  }
}
