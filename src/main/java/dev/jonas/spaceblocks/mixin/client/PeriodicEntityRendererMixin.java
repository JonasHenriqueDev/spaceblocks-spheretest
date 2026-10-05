package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.*;
import dev.jonas.spaceblocks.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(EntityRenderDispatcher.class)
abstract class PeriodicEntityRendererMixin {
  @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
  private void visible(
      Entity entity,
      Frustum frustum,
      double x,
      double y,
      double z,
      CallbackInfoReturnable<Boolean> ci) {
    var d = Planet.of(entity.level());
    if (d != null) {
      var delta = d.delta(new net.minecraft.world.phys.Vec3(x, y, z), entity.position());
      ci.setReturnValue(
          Math.hypot(delta.x, delta.z) <= d.size() / 4.0
              && entity.shouldRenderAtSqrDistance(delta.lengthSqr()));
    }
  }

  @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
  private double nearestX(double x) {
    var d = PlanetClient.planet();
    return d == null ? x : PeriodicMath.wrap(x, d.size());
  }

  @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 2)
  private double nearestZ(double z) {
    var d = PlanetClient.planet();
    return d == null ? z : PeriodicMath.wrap(z, d.size());
  }

  @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
  private MultiBufferSource project(MultiBufferSource source) {
    var d = PlanetClient.planet();
    return d == null
        ? source
        : type ->
            new ProjectedConsumer(source.getBuffer(type), new Matrix4f(), d.projectionRadius());
  }
}
