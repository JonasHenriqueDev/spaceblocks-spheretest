package dev.jonas.spaceblocks.mixin.client;
import dev.jonas.spaceblocks.client.FlatSceneCache;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class)
abstract class FlatRenderCacheMixin {
    @Inject(method="renderHitOutline",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$visualOutline(com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer consumer,
            net.minecraft.world.entity.Entity entity,double x,double y,double z,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,CallbackInfo ci) {
        if(!dev.jonas.spaceblocks.client.FlatClient.active())return;
        var view=dev.jonas.spaceblocks.client.FlatVisual.view(new net.minecraft.world.phys.Vec3(x,y,z));
        if(view.corner()<.001)return;
        state.getShape(entity.level(),pos,net.minecraft.world.phys.shapes.CollisionContext.of(entity)).forAllEdges((ax,ay,az,bx,by,bz)->{
            var a=view.project(new net.minecraft.world.phys.Vec3(pos.getX()+ax,pos.getY()+ay,pos.getZ()+az));
            var b=view.project(new net.minecraft.world.phys.Vec3(pos.getX()+bx,pos.getY()+by,pos.getZ()+bz));var normal=b.subtract(a).normalize();
            for(var p:new net.minecraft.world.phys.Vec3[]{a,b})consumer.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z).setColor(0f,0f,0f,.4f).setNormal(pose.last(),(float)normal.x,(float)normal.y,(float)normal.z);
        });ci.cancel();
    }
    @Inject(method="renderSectionLayer",at=@At("TAIL"))
    private void spaceblocks$retainTerrain(RenderType type,double x,double y,double z,Matrix4f view,Matrix4f projection,CallbackInfo ci) {
        dev.jonas.spaceblocks.client.PortalRenderer.render(type,view,projection);
        if(type==RenderType.solid())dev.jonas.spaceblocks.client.OrbitBuildRenderer.render(view,projection);
    }
    @Inject(method="setSectionDirty(IIIZ)V",at=@At("HEAD"))
    private void spaceblocks$portalDirty(int x,int y,int z,boolean immediate,CallbackInfo ci){dev.jonas.spaceblocks.client.PortalRenderer.changed(x,y,z);}
}
