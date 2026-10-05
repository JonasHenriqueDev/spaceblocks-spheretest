package dev.jonas.spaceblocks.mixin.client;
import dev.jonas.spaceblocks.SpaceBlocks;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ClientChunkCache.class)
abstract class FlatChunkCacheMixin {
    @Shadow @Final private ClientLevel level;
    @Unique private final LinkedHashMap<Long,LevelChunk> spaceblocks$staged=new LinkedHashMap<>(128,.75f,true);
    @Inject(method="replaceWithPacketData",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$stage(int x,int z,FriendlyByteBuf buffer,CompoundTag tag,Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer,CallbackInfoReturnable<LevelChunk> cir) {
        if(!SpaceBlocks.isFlat(level.dimension())) return;
        long key=ChunkPos.asLong(x,z);LevelChunk chunk=spaceblocks$staged.get(key);
        if(chunk==null) { chunk=new LevelChunk(level,new ChunkPos(x,z));spaceblocks$staged.put(key,chunk); }
        chunk.replaceWithPacketData(buffer,tag,consumer);dev.jonas.spaceblocks.client.PortalRenderer.chunkChanged(x,z);level.onChunkLoaded(chunk.getPos());
        while(spaceblocks$staged.size()>8192) spaceblocks$staged.remove(spaceblocks$staged.keySet().iterator().next());
        cir.setReturnValue(chunk);
    }
    @Inject(method="getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$stagedChunk(int x,int z,ChunkStatus status,boolean required,CallbackInfoReturnable<LevelChunk> cir) {
        if(!SpaceBlocks.isFlat(level.dimension())) return;
        LevelChunk chunk=spaceblocks$staged.get(ChunkPos.asLong(x,z));if(chunk!=null) cir.setReturnValue(chunk);
    }
    @Inject(method="getLoadedChunksCount",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$count(CallbackInfoReturnable<Integer> cir) { if(SpaceBlocks.isFlat(level.dimension())) cir.setReturnValue(spaceblocks$staged.size()); }
}
