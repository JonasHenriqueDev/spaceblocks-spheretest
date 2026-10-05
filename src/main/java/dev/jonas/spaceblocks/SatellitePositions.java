package dev.jonas.spaceblocks;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Tiny index allowing saved probe chunks to resume without a nearby player. Entities stay in
 * vanilla storage.
 */
final class SatellitePositions extends SavedData {
  record Position(UUID id, String dimension, int chunkX, int chunkZ) {}

  final Map<UUID, Position> positions = new HashMap<>();
  private static final Factory<SatellitePositions> FACTORY =
      new Factory<>(SatellitePositions::new, SatellitePositions::load, null);

  static SatellitePositions get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "spaceblocks_satellites");
  }

  private static SatellitePositions load(CompoundTag tag, HolderLookup.Provider lookup) {
    var data = new SatellitePositions();
    var list = tag.getList("probes", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(list.size(), 4096); i++) {
      var t = list.getCompound(i);
      if (t.hasUUID("id")) {
        var p =
            new Position(t.getUUID("id"), t.getString("dimension"), t.getInt("x"), t.getInt("z"));
        data.positions.put(p.id, p);
      }
    }
    return data;
  }

  void remember(Position p) {
    if (!p.equals(positions.put(p.id, p))) setDirty();
  }

  void remove(UUID id) {
    if (positions.remove(id) != null) setDirty();
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    var list = new ListTag();
    for (var p : positions.values()) {
      var t = new CompoundTag();
      t.putUUID("id", p.id);
      t.putString("dimension", p.dimension);
      t.putInt("x", p.chunkX);
      t.putInt("z", p.chunkZ);
      list.add(t);
    }
    tag.put("probes", list);
    return tag;
  }
}
