package dev.jonas.spaceblocks;

import java.util.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small procedural cabins, underground chambers and mine galleries, evaluated in toroidal cells.
 */
public final class PeriodicStructures {
  public record Column(int dx, int dz, int floor, boolean cabin, long lootSeed) {
    public BlockState block(int y) {
      int dy = y - floor;
      if (cabin) {
        if (Math.abs(dx) > 4 || Math.abs(dz) > 4 || dy < -4 || dy > 5) return null;
        if (dy < 0) return Blocks.COBBLESTONE.defaultBlockState();
        if (dy == 0 || dy == 5) return Blocks.OAK_PLANKS.defaultBlockState();
        if (dx == 2 && dz == 2 && dy == 1) return Blocks.CHEST.defaultBlockState();
        if (dx == 0 && dz == -4 && (dy == 1 || dy == 2)) return Blocks.AIR.defaultBlockState();
        if (Math.abs(dx) == 4 || Math.abs(dz) == 4)
          return (dy == 3 && (dx == 0 || dz == 0) ? Blocks.GLASS : Blocks.OAK_PLANKS)
              .defaultBlockState();
        return Blocks.AIR.defaultBlockState();
      }
      // Dungeon and intersecting galleries. Wood supports every eight blocks.
      if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4 && dy >= 0 && dy <= 5) {
        if (dy == 0 || dy == 5 || Math.abs(dx) == 4 || Math.abs(dz) == 4) {
          if (dy >= 1
              && dy <= 3
              && ((dx == 0 && Math.abs(dz) == 4) || (dz == 0 && Math.abs(dx) == 4)))
            return Blocks.AIR.defaultBlockState();
          return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (dy == 1 && dx == 2 && dz == 2) return Blocks.CHEST.defaultBlockState();
        if (dy == 1 && dx == 0 && dz == 0) return Blocks.SPAWNER.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
      }
      if (((Math.abs(dx) <= 2 && Math.abs(dz) < 40) || (Math.abs(dz) <= 2 && Math.abs(dx) < 40))
          && dy >= 0
          && dy <= 4) {
        if (dy == 0) return Blocks.OAK_PLANKS.defaultBlockState();
        int along = Math.abs(dx) > 2 ? dx : dz, across = Math.abs(dx) > 2 ? dz : dx;
        if (Math.floorMod(along, 8) == 0 && (dy == 4 || Math.abs(across) == 2))
          return (dy == 4 ? Blocks.OAK_PLANKS : Blocks.OAK_FENCE).defaultBlockState();
        return Blocks.AIR.defaultBlockState();
      }
      return null;
    }
  }

  public static List<Column> columns(int x, int z, int size, PeriodicTerrain terrain) {
    int width = size / 12;
    var result = new ArrayList<Column>();
    int bx = Math.floorDiv(x + size / 2, width), bz = Math.floorDiv(z + size / 2, width);
    for (int i = -1; i <= 1; i++)
      for (int j = -1; j <= 1; j++) {
        int cx = Math.floorMod(bx + i, 12), cz = Math.floorMod(bz + j, 12);
        long hash =
            PeriodicTerrain.hash(
                519, cx, terrain.surface(cx * width - size / 2, cz * width - size / 2), cz);
        int rx = PeriodicMath.wrap(cx * width - size / 2 + (int) Math.floorMod(hash, width), size),
            rz =
                PeriodicMath.wrap(
                    cz * width - size / 2 + (int) Math.floorMod(hash >>> 16, width), size);
        int dx = PeriodicMath.wrap(x - rx, size), dz = PeriodicMath.wrap(z - rz, size);
        int height = terrain.surface(rx, rz);
        if (Math.abs(dx) <= 40 && Math.abs(dz) <= 40)
          result.add(new Column(dx, dz, height - 35 - (int) (hash >>> 32 & 31), false, hash));
        if (height > 66 && Math.abs(dx) <= 4 && Math.abs(dz) <= 4 && (hash & 3) == 0)
          result.add(new Column(dx, dz, height + 1, true, hash));
      }
    return result;
  }
}
