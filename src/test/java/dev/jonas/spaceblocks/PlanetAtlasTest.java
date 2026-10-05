package dev.jonas.spaceblocks;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class PlanetAtlasTest {
  @Test
  void globeClosesAtLongitudeAndRotationPreservesLength() {
    for (int i = 0; i <= 96; i++) {
      var a = AtlasGeometry.globe(0, i / 96.0, 1);
      var b = AtlasGeometry.globe(1, i / 96.0, 1);
      assertEquals(a.x(), b.x(), 1e-12);
      assertEquals(a.y(), b.y(), 1e-12);
      assertEquals(a.z(), b.z(), 1e-12);
      var p = AtlasGeometry.rotate(a, .72, -.35);
      assertEquals(1, p.x() * p.x() + p.y() * p.y() + p.z() * p.z(), 1e-12);
    }
  }

  @Test
  void cameraViewUsesOriginalExponentialProjectionAndPeriodicAlias() {
    var a = AtlasGeometry.spheretest(23, -17, 95, 0, 0, 256, 1632);
    double r = 1632 / (2.0 * Math.PI);
    var b = PeriodicMath.project(23, 31, -17, r);
    assertEquals(b.x() / r, a.x(), 1e-12);
    assertEquals((b.y() + r) / r, a.y(), 1e-12);
    assertEquals(b.z() / r, a.z(), 1e-12);
    assertEquals(a, AtlasGeometry.spheretest(23 + 1632, -17 - 1632, 95, 0, 0, 256, 1632));
  }

  @Test
  void snapshotCodecPreservesTerrainCoverageAndPosition() {
    int n = 16;
    int[] heights = new int[n * n], colors = new int[n * n];
    boolean[] confirmed = new boolean[n * n];
    for (int i = 0; i < n * n; i++) {
      heights[i] = i - 128;
      colors[i] = i * 55123;
      confirmed[i] = i % 3 == 0;
    }
    var input =
        new PlanetAtlas.Snapshot(
            "spaceblocks:periodic_small", 32, 32, n, 12.5, -20.5, heights, colors, confirmed);
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    try {
      PlanetAtlas.Snapshot.CODEC.encode(buffer, input);
      var result = PlanetAtlas.Snapshot.CODEC.decode(buffer);
      assertArrayEquals(input.heights(), result.heights());
      assertArrayEquals(input.colors(), result.colors());
      assertArrayEquals(input.confirmed(), result.confirmed());
      assertEquals(input.playerX(), result.playerX());
      assertEquals(224, result.size());
    } finally {
      buffer.release();
    }
  }

  @Test
  void snapshotRejectsUnboundedAllocationsAndCopiesInput() {
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    try {
      buffer.writeUtf("spaceblocks:periodic_small");
      buffer.writeVarInt(32);
      buffer.writeInt(32);
      buffer.writeVarInt(1000000);
      assertThrows(IllegalArgumentException.class, () -> PlanetAtlas.Snapshot.CODEC.decode(buffer));
    } finally {
      buffer.release();
    }
    var h = new int[256];
    var data =
        new PlanetAtlas.Snapshot("test", 32, 32, 16, 0, 0, h, new int[256], new boolean[256]);
    h[0] = 99;
    assertEquals(0, data.heights()[0]);
  }
}
