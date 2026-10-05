package dev.jonas.spaceblocks.mixin.client;
import dev.jonas.spaceblocks.*;
import dev.jonas.spaceblocks.client.PeriodicRenderer;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ClientChunkCache.class)
abstract class PeriodicChunkCacheMixin {
    @Shadow @Final private ClientLevel level;
    @Shadow @Final private EmptyLevelChunk emptyChunk;
    @Unique private final LinkedHashMap<Long,LevelChunk> periodic$chunks=new LinkedHashMap<>(128,.75f,true);
    @Inject(method="replaceWithPacketData",at=@At("HEAD"),cancellable=true)
    private void receive(int x,int z,FriendlyByteBuf buffer,CompoundTag tag,Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> output,CallbackInfoReturnable<LevelChunk> ci){var d=Planet.of(level);if(d==null||!d.contains(new ChunkPos(x,z)))return;long key=ChunkPos.asLong(x,z);var chunk=periodic$chunks.computeIfAbsent(key,k->new LevelChunk(level,new ChunkPos(x,z)));chunk.replaceWithPacketData(buffer,tag,output);level.onChunkLoaded(chunk.getPos());PeriodicRenderer.chunkChanged(x,z);while(periodic$chunks.size()>4096)periodic$chunks.remove(periodic$chunks.keySet().iterator().next());ci.setReturnValue(chunk);}
    @Inject(method="getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",at=@At("HEAD"),cancellable=true)
    private void get(int x,int z,ChunkStatus status,boolean required,CallbackInfoReturnable<LevelChunk> ci){var d=Planet.of(level);if(d==null)return;var c=periodic$chunks.get(ChunkPos.asLong(d.chunk(x),d.chunk(z)));ci.setReturnValue(c!=null?c:required?emptyChunk:null);}
    @Inject(method="drop",at=@At("HEAD"),cancellable=true)
    private void keep(ChunkPos p,CallbackInfo ci){if(Planet.of(level)!=null)ci.cancel();}
    @Inject(method="getLoadedChunksCount",at=@At("HEAD"),cancellable=true)
    private void count(CallbackInfoReturnable<Integer> ci){if(Planet.of(level)!=null)ci.setReturnValue(periodic$chunks.size());}
}
