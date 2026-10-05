package dev.jonas.spaceblocks.mixin;
import dev.jonas.spaceblocks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Level.class)
abstract class PeriodicLevelMixin {
    @Inject(method="getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",at=@At("HEAD"),cancellable=true)
    private void periodicChunk(int x,int z,ChunkStatus status,boolean required,CallbackInfoReturnable<ChunkAccess> ci){var level=(Level)(Object)this;var d=Planet.of(level);if(d!=null&&(x!=d.chunk(x)||z!=d.chunk(z)))ci.setReturnValue(level.getChunk(d.chunk(x),d.chunk(z),status,required));}
    @ModifyVariable(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),argsOnly=true)
    private BlockPos canonicalWrite(BlockPos p){var d=Planet.of((Level)(Object)this);return d==null?p:d.canonical(p);}
    @ModifyVariable(method={"getBlockEntity","removeBlockEntity"},at=@At("HEAD"),argsOnly=true)
    private BlockPos canonicalInventory(BlockPos p){var d=Planet.of((Level)(Object)this);return d==null?p:d.canonical(p);}
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
    private void notifyPeriodic(BlockPos p,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> ci){if(ci.getReturnValue()&&(Object)this instanceof ServerLevel s)PlanetServer.changed(s,p);}
}
