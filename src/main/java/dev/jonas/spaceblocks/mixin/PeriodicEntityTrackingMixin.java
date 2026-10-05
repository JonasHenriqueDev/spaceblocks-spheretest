package dev.jonas.spaceblocks.mixin;
import dev.jonas.spaceblocks.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(targets="net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class PeriodicEntityTrackingMixin {
    @ModifyVariable(method="updatePlayer",at=@At("STORE"),ordinal=0)
    private Vec3 periodicDistance(Vec3 v,ServerPlayer player){var d=Planet.of(player.level());return d==null?v:new Vec3(PeriodicMath.wrap(v.x,d.size()),v.y,PeriodicMath.wrap(v.z,d.size()));}
}
