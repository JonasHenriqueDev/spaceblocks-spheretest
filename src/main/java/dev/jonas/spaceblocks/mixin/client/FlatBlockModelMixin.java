package dev.jonas.spaceblocks.mixin.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.jonas.spaceblocks.client.FlatClient;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ModelBlockRenderer.class)
abstract class FlatBlockModelMixin {
    private static final String METHOD="tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";
    // Rotate only horizontal texture coordinates; geometry, face culling and AO stay in the physical chart.
    @ModifyVariable(method="putQuadData",at=@At("HEAD"),argsOnly=true)
    private net.minecraft.client.renderer.block.model.BakedQuad rotateTexture(net.minecraft.client.renderer.block.model.BakedQuad quad,
            BlockAndTintGetter level,BlockState state,BlockPos pos,VertexConsumer consumer,PoseStack.Pose pose,
            net.minecraft.client.renderer.block.model.BakedQuad original,float b0,float b1,float b2,float b3,int l0,int l1,int l2,int l3,int overlay) {
        if(!FlatClient.active()||quad.getDirection().getAxis()!=net.minecraft.core.Direction.Axis.Y||!state.rotate(Rotation.CLOCKWISE_90).equals(state))return quad;
        var d=FlatClient.surface;var p=d.locate(pos.getX()+.5,pos.getZ()+.5);if(p==null)return quad;
        int turns=d.quarterTurns(p);if(turns==0)return quad;
        int[] vertices=quad.getVertices().clone();int stride=vertices.length/4;var sprite=quad.getSprite();
        float centerU=(sprite.getU0()+sprite.getU1())*.5f,centerV=(sprite.getV0()+sprite.getV1())*.5f;
        float width=sprite.getU1()-sprite.getU0(),height=sprite.getV1()-sprite.getV0();
        for(int i=0;i<4;i++) {
            float u=(Float.intBitsToFloat(vertices[i*stride+4])-centerU)/width,v=(Float.intBitsToFloat(vertices[i*stride+5])-centerV)/height;
            for(int t=0;t<turns;t++){float previous=u;u=-v;v=previous;}
            vertices[i*stride+4]=Float.floatToRawIntBits(u*width+centerU);vertices[i*stride+5]=Float.floatToRawIntBits(v*height+centerV);
        }
        return new net.minecraft.client.renderer.block.model.BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),sprite,quad.isShade(),quad.hasAmbientOcclusion());
    }
    @ModifyVariable(method=METHOD,at=@At("HEAD"),argsOnly=true)
    private long canonicalSeed(long seed,BlockAndTintGetter level,BakedModel model,BlockState state,BlockPos pos,PoseStack pose,VertexConsumer v,boolean check,RandomSource random,long original,int overlay,ModelData data,RenderType type) {
        if(!FlatClient.active())return seed;var d=FlatClient.surface;var p=d.locate(pos.getX()+.5,pos.getZ()+.5);
        return p==null?seed:state.getSeed(d.address(p,pos.getY()));
    }
}
