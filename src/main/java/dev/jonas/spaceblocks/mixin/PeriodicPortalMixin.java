package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(NetherPortalBlock.class)
abstract class PeriodicPortalMixin {
  @ModifyVariable(method = "getPortalDestination", at = @At("STORE"), ordinal = 0)
  private ResourceKey<Level> periodicReturn(
      ResourceKey<Level> destination, ServerLevel source, Entity entity, BlockPos entered) {
    var data = entity.getPersistentData();
    if (!source.dimension().equals(Level.NETHER)) {
      if (Planet.of(source) != null)
        data.putString("spaceblocks_portal_origin", source.dimension().location().toString());
      else data.remove("spaceblocks_portal_origin");
    } else {
      String origin = data.getString("spaceblocks_portal_origin");
      for (var key : java.util.List.of(SpaceBlocks.NATURAL, SpaceBlocks.LARGE, SpaceBlocks.SMALL))
        if (key.location().toString().equals(origin) && source.getServer().getLevel(key) != null)
          return key;
    }
    return destination;
  }

  @ModifyVariable(method = "getExitPortal", at = @At("HEAD"), argsOnly = true, ordinal = 1)
  private BlockPos canonicalExit(
      BlockPos exit,
      ServerLevel target,
      Entity entity,
      BlockPos entered,
      BlockPos originalExit,
      boolean nether,
      net.minecraft.world.level.border.WorldBorder border) {
    var d = Planet.of(target);
    return d == null ? exit : d.canonical(exit);
  }
}
