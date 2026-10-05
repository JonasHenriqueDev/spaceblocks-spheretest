package dev.jonas.spaceblocks.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jonas.spaceblocks.client.*;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ParticleEngine.class)
abstract class PeriodicParticlesMixin {
  @Redirect(
      method =
          "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
  private void particles(Particle p, VertexConsumer consumer, Camera camera, float partial) {
    var d = PlanetClient.planet();
    p.render(
        d == null ? consumer : new ProjectedConsumer(consumer, new Matrix4f(), d.radius()),
        camera,
        partial);
  }
}
