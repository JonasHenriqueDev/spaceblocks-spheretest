package dev.jonas.spaceblocks.surface;

import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.SpaceCommands;
import dev.jonas.spaceblocks.network.FlatNetwork;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

public final class FlatMotion {
    public static final String FLIGHT="spaceblocks_flat_flight";
    private static final Map<ServerPlayer,ArrayDeque<Long>> PREFETCH=new WeakHashMap<>();
    private static final Map<ServerPlayer,String> PREFETCH_ID=new WeakHashMap<>();
    private static final Map<ServerLevel,Set<BlockPos>> CHANGED=new WeakHashMap<>();
    private static boolean syncing;
    private FlatMotion() {}
    public static void place(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if(event.getLevel() instanceof ServerLevel level && generator(level)!=null && event.getPlacedBlock().hasBlockEntity()) {
            event.setCanceled(true);
            if(event.getEntity() instanceof ServerPlayer player)player.displayClientMessage(Component.literal("Blocos com inventário ainda não estão disponíveis neste protótipo."),true);
        }
    }
    public static FlatChunkGenerator generator(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof FlatChunkGenerator g?g:null;
    }
    public static void started(ServerStartedEvent event) {
        for(ServerLevel level:event.getServer().getAllLevels()) {
            var g=generator(level);
            if(g!=null) g.edits=level.getDataStorage().computeIfAbsent(FlatEdits.FACTORY,"spaceblocks_flat_edits");
        }
    }
    public static void changed(ServerLevel level,BlockPos pos) {
        if(!syncing && generator(level)!=null) CHANGED.computeIfAbsent(level,k->new HashSet<>()).add(pos.immutable());
    }
    public static int enter(CommandSourceStack source,boolean test) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return enter(source,test?SpaceBlocks.FLAT_TEST:SpaceBlocks.FLAT_TERRAIN);
    }
    public static int enter(CommandSourceStack source,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();
        var level=source.getServer().getLevel(dimension);
        if(level==null) { source.sendFailure(Component.literal("Dimensão do mapa em cruz indisponível."));return 0; }
        SpaceCommands.remember(player);
        dev.jonas.spaceblocks.physics.RadialMotion.forget(player);
        var d=generator(level).surface();
        Position p=new Position(Face.FRONT,d.faceSize()*.5+.5,d.faceSize()*.5+.5);
        Vec3 target=surfaceLanding(level,d,p);
        level.getChunk((int)target.x>>4,(int)target.z>>4);
        player.teleportTo(level,target.x,target.y,target.z,Set.of(),0,0);
        player.setDeltaMovement(Vec3.ZERO);setFlight(player,false);FlatNetwork.definition(player,d);
        var edits=generator(level).edits;if(edits!=null)FlatNetwork.scene(player,true,edits.snapshot());
        source.sendSuccess(()->Component.literal("Superfície plana: WASD, Espaço e Ctrl. /planet view: subir. /planet leave: voltar."),false);
        return 1;
    }
    public static int move(CommandSourceStack source,boolean view) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();var g=generator(player.serverLevel());if(g==null) return enter(source,false);
        var d=g.surface();var p=d.locate(player.getX(),player.getZ());if(p==null) return enter(source,false);
        var c=canonical(p,d.faceSize());Vec3 point=view?d.storage(c,64+Math.max(280,d.radius()*1.8)):surfaceLanding(player.serverLevel(),d,c);
        // Flat-world altitude is represented visually; provide ample vertical room for orbital inspection.
        point=new Vec3(point.x,Math.min(900,point.y),point.z);
        player.connection.teleport(point.x,point.y,point.z,player.getYRot(),view?90:0);
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;setFlight(player,view);return 1;
    }
    /** Prefer nearby intact ground so returning from a dug shaft does not drop into it again. */
    private static Vec3 surfaceLanding(ServerLevel level,FlatDefinition d,Position position) {
        for(int radius=0;radius<=8;radius++)for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            var p=canonical(new Position(position.face(),Math.floor(position.u())+dx+.5,Math.floor(position.v())+dz+.5),d.faceSize());
            var point=d.storage(p,0);int x=(int)Math.floor(point.x),z=(int)Math.floor(point.z);
            int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(y<d.height(p)+1||y+2>=level.getMaxBuildHeight())continue;
            var feet=new BlockPos(x,y,z);
            if(level.getBlockState(feet).getCollisionShape(level,feet).isEmpty()&&level.getBlockState(feet.above()).getCollisionShape(level,feet.above()).isEmpty())return new Vec3(x+.5,y+1,z+.5);
        }
        var point=d.storage(position,0);int x=(int)Math.floor(point.x),z=(int)Math.floor(point.z);
        int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        return new Vec3(x+.5,Math.max(d.height(position)+2,y+1),z+.5);
    }
    public static void setFlight(ServerPlayer p,boolean flight) {
        p.getPersistentData().putBoolean(FLIGHT,flight);applyAbilities(p,flight);
    }
    private static void applyAbilities(ServerPlayer p,boolean flight) {
        boolean changed=p.getAbilities().mayfly!=flight||p.getAbilities().flying!=flight;
        p.getAbilities().mayfly=flight;p.getAbilities().flying=flight;p.setNoGravity(flight||p.isSpectator());
        if(changed) p.onUpdateAbilities();
    }
    public static void serverTick(ServerTickEvent.Post event) {
        for(var e:new ArrayList<>(CHANGED.entrySet())) {
            var level=e.getKey();var g=generator(level);if(g==null) continue;
            if(g.edits==null) g.edits=level.getDataStorage().computeIfAbsent(FlatEdits.FACTORY,"spaceblocks_flat_edits");
            var d=g.surface();syncing=true;var changes=new HashMap<Long,BlockState>();
            try { for(BlockPos pos:e.getValue()) {
                var source=d.locate(pos.getX()+.5,pos.getZ()+.5);if(source==null) continue;
                var address=d.address(source,pos.getY());
                BlockState state=level.getBlockState(pos).rotate(Rotation.values()[d.quarterTurns(source)]);
                g.edits.put(address,state);changes.put(address.asLong(),state);
                Vec3 core=d.storage(d.fromAddress(address),pos.getY());PortalStreaming.changed(level,BlockPos.containing(core));
                for(Position alias:aliases(d,d.fromAddress(address))) {
                    Vec3 storage=d.storage(alias,pos.getY());BlockPos target=BlockPos.containing(storage);
                    if(target.equals(pos) || level.getChunkSource().getChunkNow(target.getX()>>4,target.getZ()>>4)==null) continue;
                    // Tile entities need a separate shared-instance abstraction; never silently duplicate inventory.
                    if(!state.hasBlockEntity()) level.setBlock(target,state.rotate(Rotation.values()[Math.floorMod(-d.quarterTurns(alias),4)]),3);
                }
            } } finally { syncing=false; }
            for(var player:level.players())FlatNetwork.scene(player,false,changes);
        }
        CHANGED.clear();
        for(var player:event.getServer().getPlayerList().getPlayers()) {
            var g=generator(player.serverLevel());if(g==null) { PREFETCH.remove(player);PREFETCH_ID.remove(player);continue; }
            if(player.isSpectator()||!player.isAlive()) continue;
            var d=g.surface();applyAbilities(player,player.getPersistentData().getBoolean(FLIGHT));
            if(player.tickCount%20==0) FlatNetwork.definition(player,d);
            Position p=d.locate(player.getX(),player.getZ());
            if(p==null) continue;
            var crossed=wrap(p,player.getDeltaMovement(),player.getYRot(),d.faceSize());
            if(crossed.crossings()>0) rebase(player,d,crossed,player.getY(),player.getXRot());
            else { prefetch(player,d,p);PortalStreaming.tick(player,d,p); }
            var queue=PREFETCH.get(player);
            if(queue!=null && !queue.isEmpty()) {
                var c=new net.minecraft.world.level.ChunkPos(queue.removeFirst());
                var chunk=player.serverLevel().getChunk(c.x,c.z);
                player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk,player.serverLevel().getLightEngine(),null,null));
            }
        }
    }
    public static void rebase(ServerPlayer player,FlatDefinition d,Transition t,double y,float pitch) {
        Vec3 target=d.storage(t.position(),y),velocity=t.velocity();
        SpaceBlocks.LOGGER.debug("FLAT_SEAM_SERVER: chart={} yaw={} pitch={}",t.position().face(),t.yaw(),pitch);
        player.serverLevel().getChunk((int)Math.floor(target.x)>>4,(int)Math.floor(target.z)>>4);
        player.connection.teleport(target.x,target.y,target.z,t.yaw(),pitch);
        player.setDeltaMovement(velocity);
        player.serverLevel().getChunkSource().move(player);player.connection.resetPosition();
        FlatNetwork.definition(player,d);
    }
    private static void prefetch(ServerPlayer player,FlatDefinition d,Position p) {
        double[] distances={p.u(),d.faceSize()-p.u(),p.v(),d.faceSize()-p.v()};
        Edge[] edges={Edge.WEST,Edge.EAST,Edge.NORTH,Edge.SOUTH};
        int best=0;for(int i=1;i<4;i++) if(distances[i]<distances[best]) best=i;
        if(distances[best]>64) return;
        Edge edge=edges[best];
        Position outside=switch(edge) {
            case WEST->new Position(p.face(),-.5,p.v());case EAST->new Position(p.face(),d.faceSize()+.5,p.v());
            case NORTH->new Position(p.face(),p.u(),-.5);case SOUTH->new Position(p.face(),p.u(),d.faceSize()+.5);
        };
        Position target=canonical(outside,d.faceSize());Vec3 location=d.storage(target,64);
        int cx=(int)Math.floor(location.x)>>4,cz=(int)Math.floor(location.z)>>4;
        String key=p.face()+":"+edge+":"+cx+":"+cz;
        if(key.equals(PREFETCH_ID.get(player))) return;
        PREFETCH_ID.put(player,key);ArrayDeque<Long> queue=new ArrayDeque<>();
        for(int radius=0;radius<=3;radius++) for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++)
            if(Math.max(Math.abs(x),Math.abs(z))==radius) queue.add(net.minecraft.world.level.ChunkPos.asLong(cx+x,cz+z));
        PREFETCH.put(player,queue);
    }
    /** Unfold a canonical block into every nearby chart apron, verifying its inverse address. */
    public static List<Position> aliases(FlatDefinition d,Position canonical) {
        List<Position> result=new ArrayList<>();var queue=new ArrayDeque<Position>();var seen=new HashSet<String>();
        queue.add(canonical);BlockPos address=d.address(canonical,0);
        for(int depth=0;depth<3;depth++) {
            int count=queue.size();
            for(int i=0;i<count;i++) {
                Position p=queue.removeFirst();String key=p.face()+":"+Math.round(p.u()*2)+":"+Math.round(p.v()*2);
                if(!seen.add(key)) continue;
                if(p.u()>=-d.guardSize()&&p.u()<d.faceSize()+d.guardSize()&&p.v()>=-d.guardSize()&&p.v()<d.faceSize()+d.guardSize()
                        && d.address(p,0).equals(address)) result.add(p);
                for(Edge edge:Edge.values()) queue.add(cross(p,Vec3.ZERO,0,d.faceSize(),edge).position());
            }
        }
        return result;
    }
}
