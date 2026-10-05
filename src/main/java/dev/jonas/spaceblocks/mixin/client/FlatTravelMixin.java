package dev.jonas.spaceblocks.mixin.client;
import dev.jonas.spaceblocks.client.FlatVisual;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Player.class)
abstract class FlatTravelMixin {
    @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
    private Vec3 spaceblocks$visualControls(Vec3 input) { return FlatVisual.movement(input,(Player)(Object)this); }
}
