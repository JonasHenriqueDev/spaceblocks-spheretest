package dev.jonas.spaceblocks.mixin.client;
import dev.jonas.spaceblocks.client.*;
import net.minecraft.client.renderer.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GameRenderer.class)
abstract class FlatShaderMixin {
    @Inject(method="getRendertypeSolidShader",at=@At("HEAD"),cancellable=true)
    private static void solid(CallbackInfoReturnable<ShaderInstance> c) { var s=FlatShaders.choose(RenderType.solid());if(s!=null)c.setReturnValue(s); }
    @Inject(method="getRendertypeCutoutShader",at=@At("HEAD"),cancellable=true)
    private static void cutout(CallbackInfoReturnable<ShaderInstance> c) { var s=FlatShaders.choose(RenderType.cutout());if(s!=null)c.setReturnValue(s); }
    @Inject(method="getRendertypeCutoutMippedShader",at=@At("HEAD"),cancellable=true)
    private static void mipped(CallbackInfoReturnable<ShaderInstance> c) { var s=FlatShaders.choose(RenderType.cutoutMipped());if(s!=null)c.setReturnValue(s); }
    @Inject(method="getRendertypeTranslucentShader",at=@At("HEAD"),cancellable=true)
    private static void translucent(CallbackInfoReturnable<ShaderInstance> c) { var s=FlatShaders.choose(RenderType.translucent());if(s!=null)c.setReturnValue(s); }
    @Inject(method="getDepthFar",at=@At("RETURN"),cancellable=true)
    private void far(CallbackInfoReturnable<Float> c) { if(FlatClient.active())c.setReturnValue(Math.max(c.getReturnValue(),(float)FlatClient.surface.radius()*5+1024)); }
}
