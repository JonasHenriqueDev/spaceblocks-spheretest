package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PortalForcer.class)
abstract class PeriodicPortalSearchMixin {
  @Shadow @Final private ServerLevel level;

  @Inject(method = "findClosestPortalPosition", at = @At("HEAD"), cancellable = true)
  private void connectedPortals(
      BlockPos exit,
      boolean nether,
      WorldBorder border,
      CallbackInfoReturnable<Optional<BlockPos>> ci) {
    var d = Planet.of(level);
    if (d == null) return;
    var canonical = d.canonical(exit);
    var candidates = new HashSet<BlockPos>();
    var poi = level.getPoiManager();
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++)
        poi.getInSquare(
                type -> type.is(PoiTypes.NETHER_PORTAL),
                canonical.offset(x * d.size(), 0, z * d.size()),
                nether ? 16 : 128,
                PoiManager.Occupancy.ANY)
            .map(PoiRecord::getPos)
            .forEach(candidates::add);
    ci.setReturnValue(
        candidates.stream()
            .filter(border::isWithinBounds)
            .filter(p -> level.getBlockState(p).hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
            .min(
                Comparator.comparingDouble(
                    p -> d.delta(Vec3.atCenterOf(canonical), Vec3.atCenterOf(p)).lengthSqr())));
  }
}
