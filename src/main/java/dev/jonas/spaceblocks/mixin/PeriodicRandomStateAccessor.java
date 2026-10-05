package dev.jonas.spaceblocks.mixin;

import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomState.class)
public interface PeriodicRandomStateAccessor {
  @Mutable
  @Accessor("router")
  void spaceblocks$router(NoiseRouter router);
}
