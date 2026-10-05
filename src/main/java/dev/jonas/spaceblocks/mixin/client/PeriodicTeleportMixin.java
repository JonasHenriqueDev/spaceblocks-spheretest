package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.client.PlanetClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ClientPacketListener.class)
abstract class PeriodicTeleportMixin {
  @Inject(method = "handleMovePlayer", at = @At("HEAD"))
  private void preserveVelocity(CallbackInfo ci) {
    var mc = Minecraft.getInstance();
    if (mc.isSameThread() && PlanetClient.active() && mc.player != null)
      PlanetClient.beforeTeleport = mc.player.getDeltaMovement();
  }
}
