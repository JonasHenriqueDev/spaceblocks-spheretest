package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.client.PlanetClient;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class PeriodicBlockEntityMixin {
  @Inject(method = "render", at = @At("HEAD"), cancellable = true)
  private void skipNative(CallbackInfo ci) {
    if (PlanetClient.active()) ci.cancel();
  }
}
