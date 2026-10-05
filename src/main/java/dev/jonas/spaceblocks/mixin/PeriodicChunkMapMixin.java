package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ChunkMap.class)
abstract class PeriodicChunkMapMixin {
  @Shadow @Final private ServerLevel level;

  @Inject(method = "isChunkTracked", at = @At("HEAD"), cancellable = true)
  private void periodicTracking(
      ServerPlayer player, int x, int z, CallbackInfoReturnable<Boolean> ci) {
    if (Planet.of(level) != null || Planet.of(player.level()) != null)
      ci.setReturnValue(player.level() == level && PlanetServer.tracked(player, x, z));
  }
}
