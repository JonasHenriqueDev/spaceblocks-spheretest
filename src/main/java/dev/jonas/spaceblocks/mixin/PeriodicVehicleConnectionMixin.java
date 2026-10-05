package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.Planet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class PeriodicVehicleConnectionMixin {
  @Shadow public ServerPlayer player;
  @Shadow
  private double vehicleFirstGoodX,
      vehicleFirstGoodY,
      vehicleFirstGoodZ,
      vehicleLastGoodX,
      vehicleLastGoodY,
      vehicleLastGoodZ;

  @Inject(method = "resetPosition", at = @At("TAIL"))
  private void vehicleBaseline(CallbackInfo ci) {
    var vehicle = player.getRootVehicle();
    if (vehicle == player || Planet.of(player.level()) == null) return;
    vehicleFirstGoodX = vehicleLastGoodX = vehicle.getX();
    vehicleFirstGoodY = vehicleLastGoodY = vehicle.getY();
    vehicleFirstGoodZ = vehicleLastGoodZ = vehicle.getZ();
  }
}
