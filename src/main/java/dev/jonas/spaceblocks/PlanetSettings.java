package dev.jonas.spaceblocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlanetSettings extends SavedData {
  public boolean realisticGravity = false, centrifugal = true, fallthrough = true, airDrag = true;
  public boolean hasTunnel;
  public boolean hasLab;
  public int tunnelX, tunnelZ, tunnelTop;
  public static final Factory<PlanetSettings> FACTORY =
      new Factory<>(PlanetSettings::new, PlanetSettings::load, null);

  public static PlanetSettings get(Level level) {
    return level instanceof ServerLevel s
        ? s.getDataStorage()
            .computeIfAbsent(
                s.getChunkSource().getGenerator() instanceof PlanetGenerator g && g.natural
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
    s.airDrag = !tag.contains("air_drag") || tag.getBoolean("air_drag");
    s.hasTunnel = tag.getBoolean("has_tunnel");
    s.hasLab = tag.getBoolean("has_lab");
    s.tunnelX = tag.getInt("tunnel_x");
    s.tunnelZ = tag.getInt("tunnel_z");
    s.tunnelTop = tag.getInt("tunnel_top");
    return s;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    tag.putBoolean("realistic_gravity", realisticGravity);
    tag.putBoolean("centrifugal", centrifugal);
    tag.putBoolean("fallthrough", fallthrough);
    tag.putBoolean("air_drag", airDrag);
    tag.putBoolean("has_tunnel", hasTunnel);
    tag.putBoolean("has_lab", hasLab);
    tag.putInt("tunnel_x", tunnelX);
    tag.putInt("tunnel_z", tunnelZ);
    tag.putInt("tunnel_top", tunnelTop);
    return tag;
  }
}
