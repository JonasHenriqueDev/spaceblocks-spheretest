package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class PlanetNetworkClientTests {
  private static int tick;
  private static boolean finished;
  private static final List<String> results = new ArrayList<>();

  private static void command(String c) {
    Minecraft.getInstance().player.connection.sendCommand(c);
  }

  private static void check(boolean condition, String name) {
    if (!condition) throw new IllegalStateException(name);
    results.add("PASS " + name);
    SpaceBlocks.LOGGER.info("PERIODIC_NETWORK {}", name);
  }

  public static void tick(ClientTickEvent.Post e) {
    if (!Boolean.getBoolean("spaceblocks.testNetworkClient") || finished) return;
    var mc = Minecraft.getInstance();
    if (mc.player == null) return;
    tick++;
    try {
      if (tick == 1) {
        check(mc.getSingleplayerServer() == null, "Separate dedicated server through TCP");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(5);
        mc.setScreen(null);
      }
      if (tick == 40) command("planet small");
      if (tick == 120) {
        check(mc.level.dimension().equals(SpaceBlocks.SMALL), "Network dimension entry");
        command("planet fly");
        command("tp @s 110.5 100 0.5 -90 0");
      }
      if (tick == 180) mc.options.keyUp.setDown(true);
      if (tick == 240) {
        mc.options.keyUp.setDown(false);
        check(mc.player.getX() < 0, "Network east seam with actual movement");
        command("setblock -112 100 4 minecraft:diamond_block");
      }
      if (tick == 260) {
        command("setblock 111 100 8 minecraft:air");
        command("setblock -112 100 8 minecraft:air");
        command("setblock -112 100 8 minecraft:redstone_lamp");
        command("setblock 111 100 8 minecraft:redstone_block");
      }
      if (tick == 320) {
        check(
            mc.level.getBlockState(new BlockPos(112, 100, 4)).is(Blocks.DIAMOND_BLOCK),
            "Network canonical block update through seam");
        check(
            mc.level
                .getBlockState(new BlockPos(-112, 100, 8))
                .getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),
            "Redstone powers lamp through seam");
        command("setblock 111 100 8 minecraft:air");
      }
      if (tick == 400) {
        check(
            !mc.level
                .getBlockState(new BlockPos(-112, 100, 8))
                .getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),
            "Delayed redstone tick crosses seam without crash");
        command("tp @s 111.5 100 4.5 -90 65");
      }
      if (tick == 440)
        mc.gameMode.startDestroyBlock(new BlockPos(112, 100, 4), net.minecraft.core.Direction.WEST);
      if (tick == 480) {
        check(
            mc.level.getBlockState(new BlockPos(-112, 100, 4)).isAir(),
            "Actual network mining packet through seam");
        command("tp @s 110.5 101 4.5 -90 45");
        command("setblock 111 100 4 minecraft:stone");
        command("item replace entity @s hotbar.0 with minecraft:diamond_block 64");
      }
      if (tick == 520) {
        mc.player.getInventory().selected = 0;
        mc.gameMode.useItemOn(
            mc.player,
            net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(
                new net.minecraft.world.phys.Vec3(112, 100.5, 4.5),
                net.minecraft.core.Direction.EAST,
                new BlockPos(111, 100, 4),
                false));
      }
      if (tick == 560) {
        check(
            mc.level.getBlockState(new BlockPos(-112, 100, 4)).is(Blocks.DIAMOND_BLOCK),
            "Actual network construction packet through seam");
        command("planet natural");
      }
      if (tick == 700) {
        check(mc.level.dimension().equals(SpaceBlocks.NATURAL), "Network natural terrain entry");
        command("planet core");
      }
      if (tick == 840) {
        check(mc.player.getY() < -460, "Network core access");
        command("planet noclip true");
      }
      if (tick == 920) {
        check(mc.player.isSpectator(), "Network noclip mode");
        command("tp @s 10 -300 10");
      }
      if (tick == 1000) {
        check(mc.player.getY() < -290, "Network collision-free interior");
        command("planet noclip false");
      }
      if (tick == 1120) {
        check(
            !mc.player.isSpectator() && mc.player.getY() > 40,
            "Network safe restoration to surface");
        command("planet leave");
      }
      if (tick == 1200) {
        check(!PlanetClient.active(), "Network return to standard world");
        Files.write(mc.gameDirectory.toPath().resolve("periodic-network-results.txt"), results);
        SpaceBlocks.LOGGER.info("PERIODIC_NETWORK_TEST_PASS");
        command("save-all flush");
      }
      if (tick == 1240) {
        command("stop");
        finished = true;
        mc.stop();
      }
    } catch (Throwable error) {
      finished = true;
      SpaceBlocks.LOGGER.error("PERIODIC_NETWORK_TEST_FAIL", error);
      try {
        Files.writeString(
            mc.gameDirectory.toPath().resolve("periodic-network-results.txt"), "FAIL " + error);
      } catch (Exception ignored) {
      }
      mc.stop();
    }
  }
}
