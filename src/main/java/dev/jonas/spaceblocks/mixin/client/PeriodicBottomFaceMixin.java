package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.*;
import dev.jonas.spaceblocks.client.PlanetClient;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
abstract class PeriodicBottomFaceMixin {
  @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
  private static void bottom(
      BlockState state,
      BlockGetter level,
      BlockPos pos,
      Direction face,
      BlockPos neighbor,
      CallbackInfoReturnable<Boolean> ci) {
    var p = PlanetClient.planet();
    if (p != null
        && SpaceBlocks.clientSettings.fallthrough
        && pos.getY() == p.bottom()
        && face == Direction.DOWN) ci.setReturnValue(true);
  }
}
