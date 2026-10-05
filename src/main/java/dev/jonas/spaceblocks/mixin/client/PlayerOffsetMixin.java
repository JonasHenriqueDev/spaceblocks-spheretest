package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.physics.RadialMotion;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
abstract class PlayerOffsetMixin {
    @Inject(method="getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$radialCrouch(AbstractClientPlayer player,float partial,CallbackInfoReturnable<Vec3> callback) {
        if(RadialMotion.active(player) && RadialMotion.session(player)!=null && player.isCrouching())
            callback.setReturnValue(RadialMotion.frame(player,partial).up().scale(-player.getScale()/8.0));
    }
}
