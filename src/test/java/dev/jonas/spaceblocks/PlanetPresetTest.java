package dev.jonas.spaceblocks;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class PlanetPresetTest {
  @Test
  void typedPacketsPreservePresetPolesSeedAndOldEntries() {
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    try {
      var action =
          new PlanetManagerNetwork.Action(1, "tropical", 128, "999", 0, 0, "", "jungle", false);
      PlanetManagerNetwork.Action.CODEC.encode(buffer, action);
      assertEquals(action, PlanetManagerNetwork.Action.CODEC.decode(buffer));
      buffer.clear();
      var list =
          new PlanetManagerNetwork.ListPacket(
              List.of(
                  new PlanetCatalog.Entry(
                      "red", "spaceblocks:generated_01", 32, 7, true, PlanetType.NETHER, false),
                  new PlanetCatalog.Entry("old", "spaceblocks:generated_02", 1024, 8, true)));
      PlanetManagerNetwork.ListPacket.CODEC.encode(buffer, list);
      assertEquals(list, PlanetManagerNetwork.ListPacket.CODEC.decode(buffer));
    } finally {
      buffer.release();
    }
  }

  @Test
  void polarBandsAreOptionalSymmetricAndPeriodic() {
    for (int r : new int[] {32, 64, 128}) {
      int width = PeriodicMath.circumference(r);
      assertFalse(PlanetType.EARTH.polar(0, width, true));
      assertTrue(PlanetType.EARTH.polar(width / 2 - 1, width, true));
      assertTrue(PlanetType.EARTH.polar(-width / 2, width, true));
      assertFalse(PlanetType.DESERT.polar(width / 2, width, false));
      for (int z = -width; z < width; z++)
        assertEquals(
            PlanetType.EARTH.polar(z, width, true), PlanetType.EARTH.polar(z + width, width, true));
    }
  }

  @Test
  void vanillaDensityAdapterClosesBothAxesWithoutNaNs() {
    PeriodicDomain.Sample source = (x, y, z) -> Math.sin(x * .07) + Math.cos(z * .09) + y * .01;
    for (int width : new int[] {224, 416, 832}) {
      for (int x = -width / 2; x < width / 2; x += 7)
        for (int z = -width / 2; z < width / 2; z += 13) {
          double a = PeriodicDomain.sample(source, x, 64, z, width);
          assertTrue(Double.isFinite(a));
          assertEquals(a, PeriodicDomain.sample(source, x + width, 64, z - width, width), 1e-12);
        }
    }
  }
}
