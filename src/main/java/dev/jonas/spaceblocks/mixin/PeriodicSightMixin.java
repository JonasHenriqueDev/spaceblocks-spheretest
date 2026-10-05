package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class PeriodicSightMixin {
  @Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
  private void periodicSight(Entity target, CallbackInfoReturnable<Boolean> ci) {
    var self = (LivingEntity) (Object) this;
    var d = Planet.of(self.level());
    if (d == null) return;
    if (target.level() != self.level()) {
      ci.setReturnValue(false);
      return;
    }
    var eye = self.getEyePosition();
    var delta = d.delta(eye, target.getEyePosition());
    ci.setReturnValue(
        delta.lengthSqr() < 128 * 128
            && self.level()
                    .clip(
                        new ClipContext(
                            eye,
                            eye.add(delta),
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            self))
                    .getType()
                == HitResult.Type.MISS);
  }
}
