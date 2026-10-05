package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.Planet;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(PathNavigationRegion.class)
abstract class PeriodicNavigationRegionMixin {
  @Shadow @Final protected Level level;

  @Redirect(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkSource;getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
  private LevelChunk periodicChunks(ChunkSource source, int x, int z) {
    var d = Planet.of(level);
    return source.getChunkNow(d == null ? x : d.chunk(x), d == null ? z : d.chunk(z));
  }
}
