package dev.jonas.spaceblocks.network;

import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.surface.*;
import dev.jonas.spaceblocks.client.FlatClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class FlatNetwork {
    public record Change(long position,int state) {}
    public record Scene(net.minecraft.resources.ResourceLocation dimension,boolean reset,java.util.List<Change> blocks) implements CustomPacketPayload {
        public static final Type<Scene> TYPE=new Type<>(SpaceBlocks.id("flat_scene"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Scene> CODEC=StreamCodec.of((b,p)->{
            b.writeResourceLocation(p.dimension);b.writeBoolean(p.reset);b.writeVarInt(p.blocks.size());for(var e:p.blocks){b.writeLong(e.position);b.writeVarInt(e.state);}
        },b->{var dimension=b.readResourceLocation();boolean reset=b.readBoolean();int count=b.readVarInt();if(count<0||count>4096)throw new IllegalArgumentException("Invalid scene packet");var blocks=new java.util.ArrayList<Change>();for(int i=0;i<count;i++)blocks.add(new Change(b.readLong(),b.readVarInt()));return new Scene(dimension,reset,blocks);});
        @Override public Type<Scene> type(){return TYPE;}
    }
    public static void scene(ServerPlayer p,boolean reset,java.util.Map<Long,net.minecraft.world.level.block.state.BlockState> changes) {
        var entries=new java.util.ArrayList<Change>();boolean first=true;
        for(var e:changes.entrySet()) {
            entries.add(new Change(e.getKey(),net.minecraft.world.level.block.Block.getId(e.getValue())));
            if(entries.size()==4096){PacketDistributor.sendToPlayer(p,new Scene(p.level().dimension().location(),reset&&first,java.util.List.copyOf(entries)));entries.clear();first=false;}
        }
        if(!entries.isEmpty()||reset&&first)PacketDistributor.sendToPlayer(p,new Scene(p.level().dimension().location(),reset&&first,java.util.List.copyOf(entries)));
    }
    public record Definition(FlatDefinition surface) implements CustomPacketPayload {
        public static final Type<Definition> TYPE=new Type<>(SpaceBlocks.id("flat_definition"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Definition> CODEC=StreamCodec.of((b,p)->{
            b.writeVarInt(p.surface.faceSize());b.writeVarInt(p.surface.guardSize());b.writeLong(p.surface.seed());b.writeBoolean(p.surface.coloredFaces());b.writeBoolean(p.surface.relief());
        },b->new Definition(new FlatDefinition(b.readVarInt(),b.readVarInt(),b.readLong(),b.readBoolean(),b.readBoolean())));
        @Override public Type<Definition> type() { return TYPE; }
    }
    public record Seam(double x,double y,double z,float yaw,float pitch) implements CustomPacketPayload {
        public static final Type<Seam> TYPE=new Type<>(SpaceBlocks.id("flat_seam"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Seam> CODEC=StreamCodec.of((b,p)->{
            b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeFloat(p.yaw);b.writeFloat(p.pitch);
        },b->new Seam(b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat()));
        @Override public Type<Seam> type() { return TYPE; }
    }
    public static void definition(ServerPlayer player,FlatDefinition d) { PacketDistributor.sendToPlayer(player,new Definition(d)); }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("6");
        registrar.playToClient(Definition.TYPE,Definition.CODEC,(p,c)->FlatClient.definition(p.surface));
        registrar.playToClient(Scene.TYPE,Scene.CODEC,(p,c)->dev.jonas.spaceblocks.client.OrbitBuildRenderer.receive(p));
        registrar.playToServer(Seam.TYPE,Seam.CODEC,(p,c)->{
            if(!(c.player() instanceof ServerPlayer player)) return;
            var g=FlatMotion.generator(player.serverLevel());if(g==null||!player.isAlive()) return;
            if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!Float.isFinite(p.yaw)||!Float.isFinite(p.pitch)) return;
            Vec3 reported=new Vec3(p.x,p.y,p.z);
            if(player.position().distanceToSqr(reported)>4) return;
            var source=g.surface().locate(p.x,p.z);var actual=g.surface().locate(player.getX(),player.getZ());
            if(source==null||actual==null||source.face()!=actual.face()) return;
            var t=CubeTopology.wrap(source,player.getDeltaMovement(),p.yaw,g.surface().faceSize());
            if(t.crossings()==0||t.crossings()>2) return;
            FlatMotion.rebase(player,g.surface(),t,p.y,net.minecraft.util.Mth.clamp(p.pitch,-90,90));
        });
    }
}

