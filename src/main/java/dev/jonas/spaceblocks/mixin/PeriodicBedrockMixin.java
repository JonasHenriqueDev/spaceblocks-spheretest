package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class PeriodicBedrockMixin {
  @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
  private void mineableBottom(
      BlockGetter getter, BlockPos position, CallbackInfoReturnable<Float> result) {
    if (((BlockState) (Object) this).is(Blocks.BEDROCK)
        && getter instanceof Level level
        && Planet.of(level) != null
        && PlanetSettings.get(level).fallthrough) result.setReturnValue(5F);
  }
}
