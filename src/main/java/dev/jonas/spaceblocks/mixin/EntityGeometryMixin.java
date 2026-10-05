package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.physics.RadialMotion;
import dev.jonas.spaceblocks.physics.RadialPhysics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityGeometryMixin {
    private Entity entity() { return (Entity)(Object)this; }
    private boolean radial() { return RadialMotion.session(entity())!=null && RadialMotion.active(entity()); }

    @Inject(method="getEyePosition()Lnet/minecraft/world/phys/Vec3;",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$eye(CallbackInfoReturnable<Vec3> callback) {
        if(radial()) callback.setReturnValue(RadialMotion.eye(entity(),1));
    }
    @Inject(method="getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$interpolatedEye(float partial,CallbackInfoReturnable<Vec3> callback) {
        if(radial()) callback.setReturnValue(RadialMotion.eye(entity(),partial));
    }
    @Inject(method="calculateViewVector",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$view(float pitch,float yaw,CallbackInfoReturnable<Vec3> callback) {
        if(radial()) callback.setReturnValue(RadialMotion.frame(entity(),1).look(yaw,pitch));
    }
    @Inject(method="getLookAngle",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$look(CallbackInfoReturnable<Vec3> callback) {
        if(radial()) callback.setReturnValue(RadialMotion.frame(entity(),1).look(entity().getYRot(),entity().getXRot()));
    }
    @Inject(method="makeBoundingBox",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$bounds(CallbackInfoReturnable<AABB> callback) {
        if(radial()) callback.setReturnValue(RadialPhysics.bounds(entity().position(),
                RadialPhysics.up(entity().position(),RadialMotion.session(entity()).body),entity().isShiftKeyDown()?1.5:1.8));
    }
    @Inject(method="applyGravity",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$itemGravity(CallbackInfo callback) {
        Entity entity=entity();
        if(entity instanceof ItemEntity && SpaceBlocks.isSpace(entity.level().dimension())) {
            // This MVP contains one body at (0,96,0); items keep ordinary axis-aligned collision boxes.
            Vec3 up=entity.position().subtract(0,96,0).normalize();
            entity.setDeltaMovement(entity.getDeltaMovement().subtract(up.scale(entity.getGravity())));
            callback.cancel();
        }
    }
}
