package dev.jonas.spaceblocks.surface;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;

/** Keeps authoritative chunks visible through portals loaded on the client before crossing. */
public final class PortalStreaming {
    private static final Map<ServerPlayer,Map<Long,Integer>> SENT=new WeakHashMap<>();
    private static final Map<ServerPlayer,ServerLevel> LEVELS=new WeakHashMap<>();
    public static void changed(ServerLevel level,BlockPos position) {
        long key=ChunkPos.asLong(position.getX()>>4,position.getZ()>>4);
        SENT.forEach((p,sent)->{if(p.serverLevel()==level)sent.remove(key);});
    }
    public static void tick(ServerPlayer player,FlatDefinition d,CubeTopology.Position position) {
        if(LEVELS.get(player)!=player.serverLevel()){
            LEVELS.put(player,player.serverLevel());SENT.remove(player);
            var edits=FlatMotion.generator(player.serverLevel()).edits;
            if(edits!=null)dev.jonas.spaceblocks.network.FlatNetwork.scene(player,true,edits.snapshot());
        }
        var sent=SENT.computeIfAbsent(player,p->new HashMap<>());
        if(player.tickCount%4!=0)return;
        var chunks=PortalTopology.chunks(d,position,176);int budget=3;
        for(var candidate:chunks) {
            if(candidate.face()==position.face())continue;
            long key=ChunkPos.asLong(candidate.x(),candidate.z());Integer at=sent.get(key);
            if(at!=null&&player.tickCount-at<240)continue;
            var chunk=player.serverLevel().getChunk(candidate.x(),candidate.z());
            player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk,player.serverLevel().getLightEngine(),null,null));
            sent.put(key,player.tickCount);if(--budget==0)break;
        }
        sent.entrySet().removeIf(e->player.tickCount-e.getValue()>1200);
    }
}
