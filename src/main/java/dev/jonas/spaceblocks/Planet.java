package dev.jonas.spaceblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record Planet(int radius, int depth) {
  public Planet(int radius) {
    this(radius, radius);
  }

  public static final int SURFACE = 64;
  public static final Planet SMALL = new Planet(32), LARGE = new Planet(256);
  public static final Planet NATURAL = new Planet(256, 560);

  public static Planet of(Level level) {
    if (level == null) return null;
    return level.dimension().equals(SpaceBlocks.SMALL)
        ? SMALL
        : level.dimension().equals(SpaceBlocks.LARGE)
            ? LARGE
            : level.dimension().equals(SpaceBlocks.NATURAL) ? NATURAL : null;
  }

  public int size() {
    return PeriodicMath.circumference(radius);
  }

  public int bottom() {
    return SURFACE - depth;
  }

  public BlockPos canonical(BlockPos p) {
    return new BlockPos(
        PeriodicMath.wrap(p.getX(), size()), p.getY(), PeriodicMath.wrap(p.getZ(), size()));
  }

  public int chunk(int c) {
    return PeriodicMath.wrap(c, size() / 16);
  }

  public ChunkPos canonical(ChunkPos c) {
    return new ChunkPos(chunk(c.x), chunk(c.z));
  }

  public Vec3 delta(Vec3 from, Vec3 to) {
    return new Vec3(
        PeriodicMath.wrap(to.x - from.x, size()),
        to.y - from.y,
        PeriodicMath.wrap(to.z - from.z, size()));
  }

  public boolean contains(ChunkPos c) {
    return c.equals(canonical(c));
  }
}
