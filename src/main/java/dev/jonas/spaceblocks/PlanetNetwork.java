package dev.jonas.spaceblocks;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class PlanetNetwork {
  public record ForgetChunk(int x, int z) implements CustomPacketPayload {
    public static final Type<ForgetChunk> TYPE = new Type<>(SpaceBlocks.id("forget_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgetChunk> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeInt(p.x);
              b.writeInt(p.z);
            },
            b -> new ForgetChunk(b.readInt(), b.readInt()));

    public Type<ForgetChunk> type() {
      return TYPE;
    }
  }

  public record BottomViewRequest(boolean visible, boolean projection)
      implements CustomPacketPayload {
    public static final Type<BottomViewRequest> TYPE = new Type<>(SpaceBlocks.id("bottom_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BottomViewRequest> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeBoolean(p.visible);
              b.writeBoolean(p.projection);
            },
            b -> new BottomViewRequest(b.readBoolean(), b.readBoolean()));

    public Type<BottomViewRequest> type() {
      return TYPE;
    }
  }

  public record Options(
      String dimension,
      int radius,
      int depth,
      boolean gravity,
      boolean centrifugal,
      boolean fallthrough,
      boolean airDrag)
      implements CustomPacketPayload {
    public static final Type<Options> TYPE = new Type<>(SpaceBlocks.id("options"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Options> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeUtf(p.dimension, 128);
              b.writeVarInt(p.radius);
              b.writeVarInt(p.depth);
              b.writeBoolean(p.gravity);
              b.writeBoolean(p.centrifugal);
              b.writeBoolean(p.fallthrough);
              b.writeBoolean(p.airDrag);
            },
            b ->
                new Options(
                    b.readUtf(128),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readBoolean(),
                    b.readBoolean(),
                    b.readBoolean(),
                    b.readBoolean()));

    @Override
    public Type<Options> type() {
      return TYPE;
    }
  }

  public record Velocity(double x, double y, double z, boolean bounce)
      implements CustomPacketPayload {
    public static final Type<Velocity> TYPE = new Type<>(SpaceBlocks.id("seam_velocity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Velocity> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeDouble(p.x);
              b.writeDouble(p.y);
              b.writeDouble(p.z);
              b.writeBoolean(p.bounce);
            },
            b -> new Velocity(b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean()));

    @Override
    public Type<Velocity> type() {
      return TYPE;
    }
  }

  public static void sync(ServerPlayer player) {
    var s = PlanetSettings.get(player.level());
    var planet = Planet.of(player.level());
    PacketDistributor.sendToPlayer(
        player,
        new Options(
            player.level().dimension().location().toString(),
            planet == null ? 0 : planet.radius(),
            planet == null ? 0 : planet.depth(),
            s.realisticGravity,
            s.centrifugal,
            s.fallthrough,
            s.airDrag));
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var r = event.registrar("9.0");
    r.playToClient(
        ForgetChunk.TYPE,
        ForgetChunk.CODEC,
        (p, c) -> dev.jonas.spaceblocks.client.BottomViewClient.forget(p));
    r.playToServer(
        BottomViewRequest.TYPE,
        BottomViewRequest.CODEC,
        (p, c) -> {
          if (c.player() instanceof ServerPlayer player)
            PlanetServer.bottomView(player, p.visible, p.projection);
        });
    PlanetManagerNetwork.register(r);
    r.playToServer(
        PlanetAtlas.Request.TYPE,
        PlanetAtlas.Request.CODEC,
        (p, c) -> {
          if (c.player() instanceof ServerPlayer player) PlanetAtlas.request(player, p.hud());
        });
    r.playToClient(
        PlanetAtlas.Snapshot.TYPE,
        PlanetAtlas.Snapshot.CODEC,
        (p, c) -> dev.jonas.spaceblocks.client.PlanetAtlasScreen.receive(p));
    r.playToClient(
        PlanetAtlas.HudSnapshot.TYPE,
        PlanetAtlas.HudSnapshot.CODEC,
        (p, c) -> dev.jonas.spaceblocks.client.PlanetHud.receive(p.data()));
    r.playToClient(
        Options.TYPE,
        Options.CODEC,
        (p, c) -> {
          if (p.radius >= 32 && p.radius <= 1024 && p.depth > 0 && p.depth <= 560)
            Planet.CLIENT_PLANETS.put(p.dimension, new Planet(p.radius, p.depth));
          var s = SpaceBlocks.clientSettings;
          s.realisticGravity = p.gravity;
          s.centrifugal = p.centrifugal;
          s.fallthrough = p.fallthrough;
          s.airDrag = p.airDrag;
        });
    r.playToClient(
        Velocity.TYPE,
        Velocity.CODEC,
        (p, c) -> dev.jonas.spaceblocks.client.PlanetClient.restoreVelocity(p));
  }
}
