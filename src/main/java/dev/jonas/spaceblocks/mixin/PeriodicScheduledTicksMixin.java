package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LevelAccessor.class)
public interface PeriodicScheduledTicksMixin {
  @ModifyVariable(
      method = {
        "createTick(Lnet/minecraft/core/BlockPos;Ljava/lang/Object;ILnet/minecraft/world/ticks/TickPriority;)Lnet/minecraft/world/ticks/ScheduledTick;",
        "createTick(Lnet/minecraft/core/BlockPos;Ljava/lang/Object;I)Lnet/minecraft/world/ticks/ScheduledTick;"
      },
      at = @At("HEAD"),
      argsOnly = true)
  private BlockPos periodicScheduledPosition(BlockPos p) {
    if (!((Object) this instanceof Level level)) return p;
    var d = Planet.of(level);
    return d == null ? p : d.canonical(p);
  }
}
