package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.PlanetGenerator;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
abstract class PeriodicWorldgenSeedMixin {
  @Shadow
  public abstract ServerLevel getLevel();

  @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
  private void planetSeed(CallbackInfoReturnable<Long> result) {
    if (getLevel().getChunkSource().getGenerator() instanceof PlanetGenerator g
        && g.nativeTerrain != null) result.setReturnValue(g.nativeTerrain.seed);
  }
}
