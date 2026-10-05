package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.client.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(GameRenderer.class)
abstract class PeriodicGamePickMixin {
  @Inject(
      method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
      at = @At("HEAD"),
      cancellable = true)
  private void pick(
      Entity entity,
      double blockReach,
      double entityReach,
      float partial,
      CallbackInfoReturnable<HitResult> ci) {
    if (PlanetClient.active())
      ci.setReturnValue(PlanetClient.pickScene(entity, blockReach, entityReach, partial));
  }
}
