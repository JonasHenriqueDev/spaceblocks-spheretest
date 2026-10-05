package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.SpaceBlocks;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Bound one unload pass so queued generation completions can run during shutdown. */
@Mixin(ChunkMap.class)
abstract class FlatChunkUnloadMixin {
    @Shadow @Final private ServerLevel level;
    @ModifyVariable(method="processUnloads",at=@At("HEAD"),argsOnly=true)
    private BooleanSupplier spaceblocks$boundedUnloads(BooleanSupplier allowance) {
        if(level.getServer().isRunning()&&!SpaceBlocks.isFlat(level.dimension()))return allowance;
        int[] checks={0};
        return ()->checks[0]++<64 && allowance.getAsBoolean();
    }
}
