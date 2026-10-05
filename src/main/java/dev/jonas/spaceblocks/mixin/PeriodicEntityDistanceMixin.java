package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
abstract class PeriodicEntityDistanceMixin {
  @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
  private void squared(double x, double y, double z, CallbackInfoReturnable<Double> ci) {
    var e = (Entity) (Object) this;
    var d = Planet.of(e.level());
    if (d != null) ci.setReturnValue(d.delta(e.position(), new Vec3(x, y, z)).lengthSqr());
  }

  @Inject(
      method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D",
      at = @At("HEAD"),
      cancellable = true)
  private void vector(Vec3 p, CallbackInfoReturnable<Double> ci) {
    var e = (Entity) (Object) this;
    var d = Planet.of(e.level());
    if (d != null) ci.setReturnValue(d.delta(e.position(), p).lengthSqr());
  }
}
