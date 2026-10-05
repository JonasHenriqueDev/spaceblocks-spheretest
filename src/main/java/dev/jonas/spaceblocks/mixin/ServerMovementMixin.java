package dev.jonas.spaceblocks.mixin;

import dev.jonas.spaceblocks.physics.RadialMotion;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;

    @Inject(method="handleMovePlayer",at=@At("HEAD"),cancellable=true)
    private void spaceblocks$authoritativeMovement(ServerboundMovePlayerPacket packet,CallbackInfo callback) {
        PacketUtils.ensureRunningOnSameThread(packet,(ServerGamePacketListenerImpl)(Object)this,player.serverLevel());
        if(RadialMotion.active(player)) {
            // Positions are never accepted from the client. Only bounded input payloads feed server physics.
            clientIsFloating=false;
            callback.cancel();
        }
    }
}
