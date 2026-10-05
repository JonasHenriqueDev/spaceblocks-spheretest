package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Minetest/Spheretest has no Minecraft-style vertical air damping in this branch. */
@Mixin(LivingEntity.class)
abstract class PeriodicAirDragMixin {
  @ModifyArgs(
      method = "travel",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V",
              ordinal = 3))
  private void verticalAir(Args args) {
    var entity = (LivingEntity) (Object) this;
    if (Planet.of(entity.level()) != null
        && !PlanetSettings.get(entity.level()).airDrag
        && !entity.isInWater()
        && !entity.isInLava()
        && !entity.isFallFlying()
        && !(entity instanceof net.minecraft.world.entity.animal.FlyingAnimal)
        && !(entity instanceof net.minecraft.world.entity.player.Player p
            && p.getAbilities().flying)) args.set(1, (Double) args.get(1) / (double) 0.98F);
  }
}
