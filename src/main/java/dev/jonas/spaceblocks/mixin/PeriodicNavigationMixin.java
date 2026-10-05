package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(PathNavigation.class)
abstract class PeriodicNavigationMixin {
  @Shadow @Final protected Mob mob;

  @ModifyVariable(
      method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
      at = @At("HEAD"),
      argsOnly = true)
  private Set<BlockPos> periodicTargets(Set<BlockPos> targets) {
    var d = Planet.of(mob.level());
    if (d == null) return targets;
    var p = mob.blockPosition();
    return targets.stream()
        .map(
            t ->
                new BlockPos(
                    p.getX() + PeriodicMath.wrap(t.getX() - p.getX(), d.size()),
                    t.getY(),
                    p.getZ() + PeriodicMath.wrap(t.getZ() - p.getZ(), d.size())))
        .collect(Collectors.toSet());
  }
}
