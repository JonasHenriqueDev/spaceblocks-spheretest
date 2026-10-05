package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.physics.RadialMotion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla body steering measures movement in world X/Z, which is wrong on a radial surface. */
@Mixin(LivingEntity.class)
abstract class LivingOrientationMixin {
    @Inject(method="tickHeadTurn", at=@At("HEAD"), cancellable=true)
    private void spaceblocks$localBodyHeading(float yaw, float animation, CallbackInfoReturnable<Float> callback) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (!RadialMotion.active(entity) || RadialMotion.session(entity) == null) return;
        entity.yBodyRot += Mth.wrapDegrees(entity.getYRot() - entity.yBodyRot) * 0.3f;
        callback.setReturnValue(animation);
    }
}
