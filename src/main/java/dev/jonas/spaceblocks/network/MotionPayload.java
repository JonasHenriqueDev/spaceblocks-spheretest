package dev.jonas.spaceblocks.network;

import dev.jonas.spaceblocks.PlanetDefinition;
import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.physics.GravityFrame;
import dev.jonas.spaceblocks.physics.RadialMotion;
import dev.jonas.spaceblocks.physics.RadialPhysics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public record MotionPayload(int entityId, int generation, int acknowledged, boolean flight,
                            PlanetDefinition body, RadialPhysics.State state) implements CustomPacketPayload {
    public static final Type<MotionPayload> TYPE=new Type<>(SpaceBlocks.id("radial_motion"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MotionPayload> CODEC=new StreamCodec<>() {
        @Override public MotionPayload decode(RegistryFriendlyByteBuf buf) {
            int entity=buf.readVarInt(), generation=buf.readVarInt(),ack=buf.readVarInt();
            boolean flight=buf.readBoolean();
            var body=new PlanetDefinition(buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readLong());
            Vec3 position=readVector(buf),velocity=readVector(buf),up=readVector(buf),north=readVector(buf);
            return new MotionPayload(entity,generation,ack,flight,body,new RadialPhysics.State(position,velocity,
                    new GravityFrame(up,north),buf.readBoolean(),buf.readBoolean()));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf,MotionPayload value) {
            buf.writeVarInt(value.entityId);buf.writeVarInt(value.generation);buf.writeVarInt(value.acknowledged);
            buf.writeBoolean(value.flight);
            var body=value.body;
            buf.writeDouble(body.centerX());buf.writeDouble(body.centerY());buf.writeDouble(body.centerZ());
            buf.writeDouble(body.radius());buf.writeLong(body.seed());
            var state=value.state;
            writeVector(buf,state.position());writeVector(buf,state.velocity());
            writeVector(buf,state.frame().up());writeVector(buf,state.frame().north());
            buf.writeBoolean(state.grounded());buf.writeBoolean(state.jumpHeld());
        }
    };
    private static Vec3 readVector(RegistryFriendlyByteBuf buf) { return new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()); }
    private static void writeVector(RegistryFriendlyByteBuf buf,Vec3 vector) { buf.writeDouble(vector.x);buf.writeDouble(vector.y);buf.writeDouble(vector.z); }
    public static MotionPayload of(Entity entity,RadialMotion.Session session) {
        return new MotionPayload(entity.getId(),session.generation,session.processed,session.flight,session.body,session.state);
    }
    @Override public Type<MotionPayload> type() { return TYPE; }
}
