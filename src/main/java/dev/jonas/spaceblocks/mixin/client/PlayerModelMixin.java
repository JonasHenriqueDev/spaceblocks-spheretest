package dev.jonas.spaceblocks.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jonas.spaceblocks.physics.RadialMotion;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class PlayerModelMixin {
    @Inject(method="setupRotations",at=@At("HEAD"))
    private void spaceblocks$orientModel(LivingEntity entity,PoseStack pose,float bob,float yaw,float partial,float scale,CallbackInfo callback) {
        if(RadialMotion.active(entity) && RadialMotion.session(entity)!=null) pose.mulPose(RadialMotion.frame(entity,partial).rotation());
    }
}
