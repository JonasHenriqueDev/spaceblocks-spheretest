package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Second, differently named loopback client; leaves the first client's script in control of
 * shutdown.
 */
public final class PlanetNetworkObserverTests {
  private static int ticks;
  private static boolean passed, failed;

  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("spaceblocks.testNetworkObserver") || failed) return;
    var mc = Minecraft.getInstance();
    if (mc.player == null) {
      if (passed) mc.stop();
      return;
    }
    ticks++;
    try {
      if (ticks == 1) {
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(4);
        mc.options.broadcastOptions();
        mc.setScreen(null);
      }
      if (ticks == 20) mc.player.connection.sendCommand("planet small");
      if (ticks == 80) {
        mc.player.connection.sendCommand("planet fly");
        mc.player.connection.sendCommand("tp @s -108.5 106 12.5");
        mc.player.connection.sendCommand("setblock -109 100 12 minecraft:emerald_block");
      }
      if (ticks > 100
          && !passed
          && mc.level.dimension().equals(SpaceBlocks.SMALL)
          && mc.level.getBlockState(new BlockPos(-109, 100, 12)).is(Blocks.GOLD_BLOCK)) {
        if (mc.player.connection.getOnlinePlayers().size() < 2)
          throw new IllegalStateException("Second player disappeared");
        passed = true;
        Files.writeString(
            mc.gameDirectory.toPath().resolve("observer-results.txt"),
            "PASS Two distinct simultaneous clients\n"
                + "PASS First client receives second client's emerald block\n"
                + "PASS Second client receives first client's gold block\n");
        SpaceBlocks.LOGGER.info("OBSERVER_CLIENT_TEST_PASS");
      }
      if (ticks > 1800 && !passed)
        throw new IllegalStateException("Concurrent client round trip timed out");
    } catch (Throwable error) {
      failed = true;
      SpaceBlocks.LOGGER.error("OBSERVER_CLIENT_TEST_FAIL", error);
      try {
        Files.writeString(
            mc.gameDirectory.toPath().resolve("observer-results.txt"), "FAIL " + error);
      } catch (Exception ignored) {
      }
      mc.stop();
    }
  }
}
