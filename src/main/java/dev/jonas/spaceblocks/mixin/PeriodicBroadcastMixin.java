package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.*;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PeriodicBroadcastMixin {
  @Shadow @Final private List<ServerPlayer> players;

  @Inject(
      method =
          "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void periodicBroadcast(
      Player except,
      double x,
      double y,
      double z,
      double radius,
      ResourceKey<Level> dimension,
      Packet<?> packet,
      CallbackInfo ci) {
    if (!dimension.equals(SpaceBlocks.SMALL)
        && !dimension.equals(SpaceBlocks.LARGE)
        && !dimension.equals(SpaceBlocks.NATURAL)) return;
    for (var p : players)
      if (p != except
          && p.level().dimension().equals(dimension)
          && Planet.of(p.level()).delta(p.position(), new Vec3(x, y, z)).lengthSqr()
              < radius * radius) p.connection.send(packet);
    ci.cancel();
  }
}
