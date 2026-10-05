package dev.jonas.spaceblocks;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class PeriodicTerrainTest {
  @Test
  void fullTerrainCavesAndClimateTileWithContinuousSlope() {
    var n = new PeriodicTerrain(1632, 94815);
    var r = new Random(27);
    for (int i = 0; i < 3000; i++) {
      int x = r.nextInt(1632) - 816, z = r.nextInt(1632) - 816, y = r.nextInt(500) - 450;
      assertEquals(n.surface(x, z), n.surface(x + 1632, z - 1632));
      assertEquals(n.biome(x, z), n.biome(x - 1632, z + 1632));
      assertEquals(n.cave(x, y, z, n.surface(x, z)), n.cave(x + 1632, y, z, n.surface(x, z)));
      double a = n.noise(816 - .001, y, z, 16, 30, 2), b = n.noise(-816 + .001, y, z, 16, 30, 2);
      assertEquals(a, b, .001);
    }
  }

  @Test
  void naturalMapContainsReliefWaterBiomesAndCaves() {
    var n = new PeriodicTerrain(1632, 94815);
    int min = 1000, max = -1000, caves = 0;
    var biomes = new HashSet<Integer>();
    for (int x = -816; x < 816; x += 32)
      for (int z = -816; z < 816; z += 32) {
        int h = n.surface(x, z);
        min = Math.min(min, h);
        max = Math.max(max, h);
        biomes.add(n.biome(x, z));
        for (int y = -400; y < 40; y += 16) if (n.cave(x, y, z, h)) caves++;
      }
    assertTrue(max - min > 40);
    assertTrue(min < 64);
    assertTrue(max > 85);
    assertTrue(biomes.size() >= 4);
    assertTrue(caves > 100);
  }

  @Test
  void structuresContinueAcrossBothEdges() {
    var terrain = new PeriodicTerrain(1632, 94815);
    var random = new Random(17);
    for (int i = 0; i < 1000; i++) {
      int x = random.nextInt(1632) - 816, z = random.nextInt(1632) - 816;
      assertEquals(
          PeriodicStructures.columns(x, z, 1632, terrain),
          PeriodicStructures.columns(x + 1632, z - 1632, 1632, terrain));
    }
  }

  @Test
  void worldSeedsChangeTerrain() {
    assertNotEquals(
        new PeriodicTerrain(1632, 1).surface(100, 100),
        new PeriodicTerrain(1632, 2).surface(100, 100));
  }
}
