package dev.jonas.spaceblocks.network;

import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.physics.RadialPhysics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InputPayload(int generation, RadialPhysics.Input input) implements CustomPacketPayload {
    public static final Type<InputPayload> TYPE = new Type<>(SpaceBlocks.id("radial_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,InputPayload> CODEC = new StreamCodec<>() {
        @Override public InputPayload decode(RegistryFriendlyByteBuf buf) {
            return new InputPayload(buf.readVarInt(),new RadialPhysics.Input(buf.readVarInt(),buf.readFloat(),buf.readFloat(),
                    buf.readBoolean(),buf.readBoolean(),buf.readBoolean(),buf.readFloat(),buf.readFloat()));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf,InputPayload value) {
            var input=value.input;
            buf.writeVarInt(value.generation);buf.writeVarInt(input.sequence());
            buf.writeFloat(input.forward());buf.writeFloat(input.left());
            buf.writeBoolean(input.jump());buf.writeBoolean(input.sprint());buf.writeBoolean(input.crouch());
            buf.writeFloat(input.yaw());buf.writeFloat(input.pitch());
        }
    };
    @Override public Type<InputPayload> type() { return TYPE; }
}
