package dev.jonas.spaceblocks;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PlanetManagerNetwork {
  public record Action(
      int action,
      String name,
      int radius,
      String seed,
      double x,
      double z,
      String height,
      String preset,
      boolean poles)
      implements CustomPacketPayload {
    public Action(
        int action, String name, int radius, String seed, double x, double z, String height) {
      this(action, name, radius, seed, x, z, height, "earth", true);
    }

    public static final Type<Action> TYPE =
        new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                SpaceBlocks.MOD_ID, "planet_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.action);
              b.writeUtf(p.name, 24);
              b.writeVarInt(p.radius);
              b.writeUtf(p.seed, 24);
              b.writeDouble(p.x);
              b.writeDouble(p.z);
              b.writeUtf(p.height, 24);
              b.writeUtf(p.preset, 16);
              b.writeBoolean(p.poles);
            },
            b ->
                new Action(
                    b.readVarInt(),
                    b.readUtf(24),
                    b.readVarInt(),
                    b.readUtf(24),
                    b.readDouble(),
                    b.readDouble(),
                    b.readUtf(24),
                    b.readUtf(16),
                    b.readBoolean()));

    public Type<Action> type() {
      return TYPE;
    }

    public static Action list() {
      return new Action(0, "", 32, "", 0, 0, "");
    }
  }

  public record ListPacket(List<PlanetCatalog.Entry> entries) implements CustomPacketPayload {
    public ListPacket {
      if (entries.size() > 20) throw new IllegalArgumentException("Too many planets");
      entries = List.copyOf(entries);
    }

    public static final Type<ListPacket> TYPE =
        new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                SpaceBlocks.MOD_ID, "planet_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ListPacket> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.entries.size());
              for (var e : p.entries) {
                b.writeUtf(e.name(), 24);
                b.writeUtf(e.dimension(), 128);
                b.writeVarInt(e.radius());
                b.writeLong(e.seed());
                b.writeBoolean(e.generated());
                b.writeUtf(e.type().id(), 16);
                b.writeBoolean(e.poles());
              }
            },
            b -> {
              int n = b.readVarInt();
              if (n < 0 || n > 20) throw new IllegalArgumentException("Invalid planet count");
              var entries = new ArrayList<PlanetCatalog.Entry>();
              for (int i = 0; i < n; i++)
                entries.add(
                    new PlanetCatalog.Entry(
                        b.readUtf(24),
                        b.readUtf(128),
                        b.readVarInt(),
                        b.readLong(),
                        b.readBoolean(),
                        PlanetType.parse(b.readUtf(16)),
                        b.readBoolean()));
              return new ListPacket(entries);
            });

    public Type<ListPacket> type() {
      return TYPE;
    }
  }

  public static void send(ServerPlayer player) {
    if (player.hasPermissions(2))
      PacketDistributor.sendToPlayer(
          player, new ListPacket(PlanetCatalog.get(player.server).entries(player.server)));
  }

  public static void register(PayloadRegistrar r) {
    r.playToClient(
        ListPacket.TYPE,
        ListPacket.CODEC,
        (p, c) -> dev.jonas.spaceblocks.client.PlanetManagerScreen.receive(p.entries));
    r.playToServer(
        Action.TYPE,
        Action.CODEC,
        (p, c) -> {
          if (!(c.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
          try {
            switch (p.action) {
              case 0 -> send(player);
              case 1 -> {
                PlanetCatalog.generate(
                    player,
                    p.name,
                    p.radius,
                    p.seed.isBlank() ? null : Long.valueOf(p.seed),
                    PlanetType.parse(p.preset),
                    p.poles);
                send(player);
              }
              case 2, 3 -> {
                if (PlanetCatalog.teleport(
                        player,
                        p.name,
                        p.x,
                        p.z,
                        p.height.isBlank() || p.height.equalsIgnoreCase("surface")
                            ? null
                            : Double.valueOf(p.height))
                    > 0) {
                  if (p.name.equals("lab")) PlanetLab.prepare(player);
                  if (p.action == 3) PlanetAtlas.request(player);
                }
              }
              default -> {}
            }
          } catch (IllegalArgumentException ex) {
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "Invalid number. Use a numeric radius, seed and coordinates, or surface for"
                        + " height."));
          }
        });
  }
}
