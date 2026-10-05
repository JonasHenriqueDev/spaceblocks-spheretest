package dev.jonas.spaceblocks.mixin;
import dev.jonas.spaceblocks.*;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ChunkMap.class)
abstract class PeriodicChunkMapMixin {
    @Inject(method="isChunkTracked",at=@At("HEAD"),cancellable=true)
    private void periodicTracking(ServerPlayer player,int x,int z,CallbackInfoReturnable<Boolean> ci){if(Planet.of(player.level())!=null)ci.setReturnValue(PlanetServer.tracked(player,x,z));}
}
