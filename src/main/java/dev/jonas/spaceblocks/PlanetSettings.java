package dev.jonas.spaceblocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
public final class PlanetSettings extends SavedData {
    public boolean realisticGravity=false,centrifugal=true,fallthrough=true;
    public static final Factory<PlanetSettings> FACTORY=new Factory<>(PlanetSettings::new,PlanetSettings::load,null);
    public static PlanetSettings get(Level level){return level instanceof ServerLevel s?s.getDataStorage().computeIfAbsent(FACTORY,"spaceblocks_physics"):SpaceBlocks.clientSettings;}
    private static PlanetSettings load(CompoundTag tag,HolderLookup.Provider provider){var s=new PlanetSettings();s.realisticGravity=tag.getBoolean("realistic_gravity");s.centrifugal=!tag.contains("centrifugal")||tag.getBoolean("centrifugal");s.fallthrough=!tag.contains("fallthrough")||tag.getBoolean("fallthrough");return s;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider){tag.putBoolean("realistic_gravity",realisticGravity);tag.putBoolean("centrifugal",centrifugal);tag.putBoolean("fallthrough",fallthrough);return tag;}
}
