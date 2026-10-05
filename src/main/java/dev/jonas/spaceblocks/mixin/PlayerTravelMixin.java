package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.physics.RadialMotion;
import dev.jonas.spaceblocks.physics.RadialPhysics;
import dev.jonas.spaceblocks.physics.WorldCollider;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerTravelMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$radialTravel(Vec3 movement,CallbackInfo callback) {
        if(RadialMotion.active((Player)(Object)this)) callback.cancel();
    }

    @Inject(method="canPlayerFitWithinBlocksAndEntitiesWhen",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$radialPose(Pose pose,CallbackInfoReturnable<Boolean> callback) {
        Player player=(Player)(Object)this;
        if(!RadialMotion.active(player) || RadialMotion.session(player)==null) return;
        var session=RadialMotion.session(player);
        double height=pose==Pose.CROUCHING?1.5:pose==Pose.SWIMMING?0.6:1.8;
        Vec3 up=RadialPhysics.up(player.position(),session.body);
        var contact=RadialPhysics.resolve(player.position(),up,height,new WorldCollider(player.level(),player));
        callback.setReturnValue(contact.position().distanceToSqr(player.position())<0.000025);
    }
}
