package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LookControl.class)
abstract class PeriodicLookControlMixin {
  @Shadow @Final protected Mob mob;

  @ModifyVariable(method = "setLookAt(DDDFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
  private double periodicX(double x) {
    var d = Planet.of(mob.level());
    return d == null ? x : mob.getX() + PeriodicMath.wrap(x - mob.getX(), d.size());
  }

  @ModifyVariable(method = "setLookAt(DDDFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
  private double periodicZ(double z) {
    var d = Planet.of(mob.level());
    return d == null ? z : mob.getZ() + PeriodicMath.wrap(z - mob.getZ(), d.size());
  }
}
