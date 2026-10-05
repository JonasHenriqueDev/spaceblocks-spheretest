package dev.jonas.spaceblocks.mixin.client;
import dev.jonas.spaceblocks.client.FlatClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
abstract class FlatMovementMixin {
    @Inject(method="sendPosition",at=@At("HEAD"))
    private void spaceblocks$predictSeam(CallbackInfo ci) { FlatClient.beforeSend((LocalPlayer)(Object)this); }
}
