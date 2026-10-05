package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.physics.RadialMotion;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenEffectRenderer.class)
abstract class BlockOverlayMixin {
    @Inject(method="getOverlayBlock",at=@At("HEAD"),cancellable=true)
    private static void spaceblocks$radialOverlay(Player player,CallbackInfoReturnable<Pair<BlockState,BlockPos>> callback) {
        if(!RadialMotion.active(player) || RadialMotion.session(player)==null) return;
        var frame=RadialMotion.frame(player,1);
        Vec3 eye=RadialMotion.eye(player,1);
        for(int corner=0;corner<8;corner++) {
            Vec3 sample=eye.add(frame.east().scale((corner&1)==0?-0.1:0.1))
                    .add(frame.up().scale((corner&2)==0?-0.05:0.05))
                    .add(frame.north().scale((corner&4)==0?-0.1:0.1));
            BlockPos position=BlockPos.containing(sample);
            BlockState block=player.level().getBlockState(position);
            if(block.getRenderShape()!=RenderShape.INVISIBLE && block.isViewBlocking(player.level(),position)) {
                callback.setReturnValue(Pair.of(block,position));return;
            }
        }
        callback.setReturnValue(null);
    }
}
