package dev.jonas.spaceblocks.mixin.client;

import dev.jonas.spaceblocks.client.PlanetClient;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class PeriodicSoundEngineMixin {
  @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

  @Inject(method = "tickNonPaused", at = @At("TAIL"))
  private void maintainPeriodicChannels(CallbackInfo ci) {
    if (PlanetClient.audio == null) return;
    instanceToChannel.forEach(
        (sound, handle) -> {
          if (!sound.isRelative())
            handle.execute(
                channel ->
                    channel.setSelfPosition(new Vec3(sound.getX(), sound.getY(), sound.getZ())));
        });
  }
}
