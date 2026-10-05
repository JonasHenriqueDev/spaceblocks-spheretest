package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.physics.RadialMotion;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void move(float zoom,float dy,float dx);
    @Shadow protected abstract void setRotation(float yaw,float pitch,float roll);
    @Shadow private float getMaxZoom(float distance) { throw new AssertionError(); }

    @Inject(method="setup",at=@At("TAIL"))
    private void spaceblocks$radialCamera(BlockGetter level,Entity entity,boolean detached,boolean reversed,float partial,CallbackInfo callback) {
        if(!RadialMotion.active(entity) || RadialMotion.session(entity)==null) return;
        float yaw=entity.getViewYRot(partial),pitch=entity.getViewXRot(partial);
        if(detached && reversed) { yaw+=180;pitch=-pitch; }
        setRotation(yaw,pitch,0);
        rotation.premul(RadialMotion.frame(entity,partial).rotation());
        forwards.set(0,0,-1).rotate(rotation);
        up.set(0,1,0).rotate(rotation);
        left.set(-1,0,0).rotate(rotation);
        setPosition(RadialMotion.eye(entity,partial));
        if(detached) move(-getMaxZoom(4),0,0);
    }
}
