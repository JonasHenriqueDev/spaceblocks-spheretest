package dev.jonas.spaceblocks.mixin;
import dev.jonas.spaceblocks.surface.FlatMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Level.class)
abstract class FlatBlockChangesMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
    private void spaceblocks$sharedBlock(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValue() && (Object)this instanceof ServerLevel level) FlatMotion.changed(level,pos);
    }
}
