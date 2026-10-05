package dev.jonas.spaceblocks.mixin.client;
import dev.jonas.spaceblocks.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
abstract class FlatPickMixin {
    @Inject(method="pick",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$visualPick(double distance,float partial,boolean fluids,CallbackInfoReturnable<HitResult> ci) {
        Entity entity=(Entity)(Object)this;
        if(FlatClient.active()&&entity==Minecraft.getInstance().player&&FlatVisual.view(entity.getEyePosition(partial)).corner()>.001)
            ci.setReturnValue(FlatVisual.pick(entity,distance,partial,fluids));
    }
}
