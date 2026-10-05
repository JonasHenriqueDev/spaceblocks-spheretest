package dev.jonas.spaceblocks.mixin.client;

import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LevelRenderer.class)
abstract class PeriodicLevelRendererMixin {
  @Inject(method = "compileSections", at = @At("HEAD"), cancellable = true)
  private void skipFlatMeshing(CallbackInfo ci) {
    if (PlanetClient.active()) ci.cancel();
  }

  @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
  private void terrain(
      RenderType type,
      double x,
      double y,
      double z,
      Matrix4f view,
      Matrix4f projection,
      CallbackInfo ci) {
    if (PlanetClient.active()) {
      PeriodicRenderer.render(type, view, projection);
      ci.cancel();
    }
  }

  @Inject(method = "isSectionCompiled", at = @At("HEAD"), cancellable = true)
  private void entitySections(BlockPos p, CallbackInfoReturnable<Boolean> ci) {
    if (PlanetClient.active()) ci.setReturnValue(true);
  }

  @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
  private void changed(int x, int y, int z, boolean immediate, CallbackInfo ci) {
    PeriodicRenderer.changed(x, y, z);
  }

  @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
  private void outline(
      PoseStack pose,
      VertexConsumer consumer,
      Entity entity,
      double x,
      double y,
      double z,
      BlockPos pos,
      BlockState state,
      CallbackInfo ci) {
    if (!PlanetClient.active()) return;
    var eye = new Vec3(x, y, z);
    state
        .getShape(entity.level(), pos, net.minecraft.world.phys.shapes.CollisionContext.of(entity))
        .forAllEdges(
            (ax, ay, az, bx, by, bz) -> {
              // Subdivide outline edges so the line follows the same nonlinear projection.
              Vec3 a = new Vec3(pos.getX() + ax, pos.getY() + ay, pos.getZ() + az),
                  b = new Vec3(pos.getX() + bx, pos.getY() + by, pos.getZ() + bz);
              for (int i = 0; i < 4; i++) {
                var pa = PlanetClient.project(a.lerp(b, i / 4.0), eye);
                var pb = PlanetClient.project(a.lerp(b, (i + 1) / 4.0), eye);
                var n = pb.subtract(pa).normalize();
                for (var p : new Vec3[] {pa, pb})
                  consumer
                      .addVertex(pose.last(), (float) p.x, (float) p.y, (float) p.z)
                      .setColor(0f, 0f, 0f, .4f)
                      .setNormal(pose.last(), (float) n.x, (float) n.y, (float) n.z);
              }
            });
    ci.cancel();
  }
}
