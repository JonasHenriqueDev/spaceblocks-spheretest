package dev.jonas.spaceblocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlanetSettings extends SavedData {
  public boolean realisticGravity = false, centrifugal = true, fallthrough = true;
  public static final Factory<PlanetSettings> FACTORY =
      new Factory<>(PlanetSettings::new, PlanetSettings::load, null);

  public static PlanetSettings get(Level level) {
    return level instanceof ServerLevel s
        ? s.getDataStorage()
            .computeIfAbsent(
                level.dimension().equals(SpaceBlocks.NATURAL)
                    ? new Factory<>(
                        () -> {
                          var result = new PlanetSettings();
                          result.fallthrough = false;
                          return result;
                        },
                        PlanetSettings::load,
                        null)
                    : FACTORY,
                "spaceblocks_physics")
        : SpaceBlocks.clientSettings;
  }

  private static PlanetSettings load(CompoundTag tag, HolderLookup.Provider provider) {
    var s = new PlanetSettings();
    s.realisticGravity = tag.getBoolean("realistic_gravity");
    s.centrifugal = !tag.contains("centrifugal") || tag.getBoolean("centrifugal");
    s.fallthrough = !tag.contains("fallthrough") || tag.getBoolean("fallthrough");
    return s;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    tag.putBoolean("realistic_gravity", realisticGravity);
    tag.putBoolean("centrifugal", centrifugal);
    tag.putBoolean("fallthrough", fallthrough);
    return tag;
  }
}
