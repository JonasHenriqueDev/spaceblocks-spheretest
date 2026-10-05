package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.Planet;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class PeriodicServerParticlesMixin {
  @Inject(
      method =
          "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void periodicParticles(
      ServerPlayer player,
      boolean distant,
      double x,
      double y,
      double z,
      Packet<?> packet,
      CallbackInfoReturnable<Boolean> ci) {
    var level = (ServerLevel) (Object) this;
    var d = Planet.of(level);
    if (d == null) return;
    boolean near =
        player.level() == level
            && d.delta(Vec3.atCenterOf(player.blockPosition()), new Vec3(x, y, z)).lengthSqr()
                < Math.pow(distant ? 512 : 32, 2);
    if (near) player.connection.send(packet);
    ci.setReturnValue(near);
  }
}
