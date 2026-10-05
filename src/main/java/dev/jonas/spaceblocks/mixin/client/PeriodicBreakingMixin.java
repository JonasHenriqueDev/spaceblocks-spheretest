package dev.jonas.spaceblocks.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jonas.spaceblocks.client.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(BlockRenderDispatcher.class)
abstract class PeriodicBreakingMixin {
  @ModifyVariable(method = "renderBreakingTexture", at = @At("HEAD"), argsOnly = true)
  private VertexConsumer cracking(VertexConsumer target) {
    var d = PlanetClient.planet();
    return d == null ? target : new ProjectedConsumer(target, new Matrix4f(), d.radius());
  }
}
