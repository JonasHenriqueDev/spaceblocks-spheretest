package dev.jonas.spaceblocks.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import dev.jonas.spaceblocks.client.PlanetClient;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Channel.class)
abstract class PeriodicSoundMixin {
  @ModifyVariable(method = "setSelfPosition", at = @At("HEAD"), argsOnly = true)
  private Vec3 periodicSound(Vec3 position) {
    if (position.equals(Vec3.ZERO)) return position;
    var snapshot = PlanetClient.audio;
    return snapshot == null
        ? position
        : snapshot.eye().add(snapshot.planet().delta(snapshot.eye(), position));
  }
}
