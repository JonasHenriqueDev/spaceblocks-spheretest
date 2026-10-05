package dev.jonas.spaceblocks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class PlanetNetwork {
    public record Options(boolean gravity,boolean centrifugal,boolean fallthrough) implements CustomPacketPayload {
        public static final Type<Options> TYPE=new Type<>(SpaceBlocks.id("options"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Options> CODEC=StreamCodec.of((b,p)->{b.writeBoolean(p.gravity);b.writeBoolean(p.centrifugal);b.writeBoolean(p.fallthrough);},b->new Options(b.readBoolean(),b.readBoolean(),b.readBoolean()));
        @Override public Type<Options> type(){return TYPE;}
    }
    public record Velocity(double x,double y,double z,boolean bounce) implements CustomPacketPayload {
        public static final Type<Velocity> TYPE=new Type<>(SpaceBlocks.id("seam_velocity"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Velocity> CODEC=StreamCodec.of((b,p)->{b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeBoolean(p.bounce);},b->new Velocity(b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean()));
        @Override public Type<Velocity> type(){return TYPE;}
    }
    public static void sync(ServerPlayer player){var s=PlanetSettings.get(player.level());PacketDistributor.sendToPlayer(player,new Options(s.realisticGravity,s.centrifugal,s.fallthrough));}
    public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("7");r.playToClient(Options.TYPE,Options.CODEC,(p,c)->{var s=SpaceBlocks.clientSettings;s.realisticGravity=p.gravity;s.centrifugal=p.centrifugal;s.fallthrough=p.fallthrough;});r.playToClient(Velocity.TYPE,Velocity.CODEC,(p,c)->dev.jonas.spaceblocks.client.PlanetClient.restoreVelocity(p));}
}
