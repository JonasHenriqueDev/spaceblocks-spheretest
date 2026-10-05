package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
abstract class PeriodicGravityMixin {
  @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
  private void gravity(CallbackInfoReturnable<Double> ci) {
    var e = (Entity) (Object) this;
    var d = Planet.of(e.level());
    if (d == null
        || e.isNoGravity()
        || e.isInWater()
        || e.isInLava()
        || e instanceof LivingEntity l && l.onClimbable()) return;
    var s = PlanetSettings.get(e.level());
    double g = ci.getReturnValue();
    if (s.realisticGravity)
      g *= PeriodicMath.gravityCoefficient(e.getY() - Planet.SURFACE, d.radius());
    if (s.centrifugal) {
      var v = e.getDeltaMovement();
      g -= PeriodicMath.centrifugal(v.x, v.z, e.getY() - Planet.SURFACE, d.radius());
    }
    ci.setReturnValue(g);
  }
}
