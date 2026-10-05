package dev.jonas.spaceblocks;

import net.minecraft.world.level.ChunkPos;

/** Authoritative periodic unloads are separate from vanilla's local cache window. */
public interface PeriodicChunkAccess {
  void dropPeriodic(ChunkPos pos);
}
