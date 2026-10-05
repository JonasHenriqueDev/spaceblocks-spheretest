package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
abstract class PeriodicPickMixin {
  @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
  private void pick(
      double distance, float partial, boolean fluid, CallbackInfoReturnable<HitResult> ci) {
    if (PlanetClient.active() && (Object) this == Minecraft.getInstance().getCameraEntity())
      ci.setReturnValue(PlanetClient.pick((Entity) (Object) this, distance, partial, fluid));
  }
}
