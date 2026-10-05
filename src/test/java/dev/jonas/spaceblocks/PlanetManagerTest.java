package dev.jonas.spaceblocks;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class PlanetManagerTest {
  @Test
  void actionsKeepRequestedSizeSeedAndCoordinates() {
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    try {
      var action =
          new PlanetManagerNetwork.Action(
              2, "aurora", 1024, "-9223372036854775808", 33.5, -41.5, "200");
      PlanetManagerNetwork.Action.CODEC.encode(buffer, action);
      assertEquals(action, PlanetManagerNetwork.Action.CODEC.decode(buffer));
    } finally {
      buffer.release();
    }
  }

  @Test
  void listRoundTripAndBoundBeforeAllocation() {
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    try {
      var packet =
          new PlanetManagerNetwork.ListPacket(
              List.of(
                  new PlanetCatalog.Entry("aurora", "spaceblocks:generated_01", 1024, 999, true)));
      PlanetManagerNetwork.ListPacket.CODEC.encode(buffer, packet);
      assertEquals(packet, PlanetManagerNetwork.ListPacket.CODEC.decode(buffer));
      buffer.clear();
      buffer.writeVarInt(1000000);
      assertThrows(
          IllegalArgumentException.class,
          () -> PlanetManagerNetwork.ListPacket.CODEC.decode(buffer));
    } finally {
      buffer.release();
    }
  }

  @Test
  void namesCannotEscapeDimensionsOrContainCommands() {
    assertTrue(PlanetCatalog.validName("aurora_2"));
    for (String name : List.of("../world", "a/b", "a;stop", "AURORA", "", "a".repeat(25)))
      assertFalse(PlanetCatalog.validName(name));
  }

  @Test
  void balancedSpeedCancelsOriginalCentrifugalTerm() {
    for (int r : new int[] {32, 64, 256, 1024})
      for (double h : new double[] {16, 128, 400}) {
        double v = PlanetSatellite.circularSpeed(h, r);
        assertEquals(
            .08 * PeriodicMath.gravityCoefficient(h, r),
            PeriodicMath.centrifugal(v, 0, h, r),
            1e-12);
      }
  }

  @Test
  void randomSeedChangesPeriodicTerrainWithoutBreakingSeams() {
    var a = new PeriodicTerrain(416, 111);
    var b = new PeriodicTerrain(416, 222);
    int different = 0;
    for (int x = -160; x <= 160; x += 16)
      for (int z = -160; z <= 160; z += 16) {
        if (a.surface(x, z) != b.surface(x, z)) different++;
        assertEquals(b.surface(x, z), b.surface(x + 416, z - 416));
      }
    assertTrue(different > 100);
  }
}
