package dev.jonas.spaceblocks.mixin.client;
import dev.jonas.spaceblocks.client.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderDispatcher.class)
abstract class FlatEntityRenderMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void spaceblocks$visualEntity(Entity e,double x,double y,double z,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light,CallbackInfo ci) {
        pose.pushPose();if(!FlatClient.active())return;
        Vec3 eye=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 shown=FlatVisual.view(eye).project(eye.add(x,y,z));pose.translate(shown.x-x,shown.y-y,shown.z-z);
    }
    @Inject(method="render",at=@At("RETURN"))
    private void spaceblocks$end(Entity e,double x,double y,double z,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light,CallbackInfo ci) {pose.popPose();}
}
