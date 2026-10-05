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
  private static long connectDeadline;
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
        connectDeadline = System.nanoTime() + 120_000_000_000L;
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
        if (Boolean.getBoolean("spaceblocks.twoClients")
            && (mc.player.connection.getOnlinePlayers().size() < 2
                || !mc.level.getBlockState(new BlockPos(-109, 100, 12)).is(Blocks.EMERALD_BLOCK))) {
          if (System.nanoTime() > connectDeadline)
            throw new IllegalStateException("Second client handshake timed out");
          tick--;
          return;
        }
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
        if (Boolean.getBoolean("spaceblocks.twoClients")) {
          check(
              mc.player.connection.getOnlinePlayers().size() >= 2,
              "Two distinct players connected simultaneously");
          check(
              mc.level.getBlockState(new BlockPos(-109, 100, 12)).is(Blocks.EMERALD_BLOCK),
              "Second player's block reaches first client");
          command("setblock -109 100 12 minecraft:gold_block");
        }
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
      if (tick == 1080) command("planet map");
      if (tick == 1120) {
        check(
            !mc.player.isSpectator() && mc.player.getY() > 40,
            "Network safe restoration to surface");
        check(mc.screen instanceof PlanetAtlasScreen, "Atlas snapshot delivered over real TCP");
        check(
            ((PlanetAtlasScreen) mc.screen).snapshot().size() == 1632,
            "TCP atlas dimensions decoded");
        mc.setScreen(null);
        command("planet leave");
      }
      if (tick == 1200) {
        check(!PlanetClient.active(), "Network return to standard world");
        command("planet generate network_planet 64 444");
      }
      if (tick == 1250) command("planet enter network_planet");
      if (tick == 1350) {
        check(
            PlanetClient.active() && PlanetClient.planet().radius() == 64,
            "TCP generates and enters a named planet with selected radius");
        command("planet map");
      }
      if (tick == 1430) {
        check(
            mc.screen instanceof PlanetAtlasScreen
                && ((PlanetAtlasScreen) mc.screen).snapshot().radius() == 64,
            "TCP atlas uses generated planet size");
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            PlanetManagerNetwork.Action.list());
      }
      if (tick == 1490) {
        check(mc.screen instanceof PlanetManagerScreen, "TCP planet manager opens");
        check(
            ((PlanetManagerScreen) mc.screen)
                .entries().stream()
                    .anyMatch(
                        p ->
                            p.name().equals("network_planet")
                                && p.radius() == 64
                                && p.seed() == 444),
            "TCP catalog retains planet name, radius and seed");
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new PlanetManagerNetwork.Action(2, "network_planet", 64, "", 32.5, 48.5, "200"));
        mc.setScreen(null);
      }
      if (tick == 1530) {
        check(
            Math.abs(mc.player.getX() - 32.5) < 1
                && Math.abs(mc.player.getZ() - 48.5) < 1
                && mc.player.getY() > 120,
            "TCP manager teleports to requested position");
        command("planet leave");
      }
      if (tick == 1600) {
        check(!PlanetClient.active(), "TCP returns home after generated planet visit");
        command("planet generate tcp_flat_090 128 flat 777 false");
        command("planet enter tcp_flat_090");
      }
      if (tick == 1740) {
        check(
            PlanetClient.active() && PlanetClient.planet().radius() == 128,
            "TCP creates flat preset with maximum requested radius");
        check(
            mc.level
                    .getChunkSource()
                    .getChunkNow(0, 0)
                    .getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, 0)
                == 64,
            "TCP flat terrain has exact surface height");
        command("planet planets");
      }
      if (tick == 1790) {
        check(mc.screen instanceof PlanetManagerScreen, "Typed manager opens over TCP");
        var screen = (PlanetManagerScreen) mc.screen;
        check(
            screen.entries().stream()
                .anyMatch(
                    p ->
                        p.name().equals("tcp_flat_090")
                            && p.type() == PlanetType.FLAT
                            && !p.poles()
                            && p.seed() == 777),
            "Manager packet retains preset and optional poles");
        var typeButton =
            (net.minecraft.client.gui.components.Button)
                screen.children().stream()
                    .filter(
                        w ->
                            w instanceof net.minecraft.client.gui.components.Button b
                                && b.getMessage().getString().startsWith("Type:"))
                    .findFirst()
                    .orElseThrow();
        for (int i = 0; i < 7; i++) typeButton.onPress();
        check(
            typeButton.getMessage().getString().equals("Type: flat"),
            "Type selector reaches flat preset");
        check(
            screen.children().stream()
                .filter(w -> w instanceof net.minecraft.client.gui.components.AbstractWidget)
                .map(w -> (net.minecraft.client.gui.components.AbstractWidget) w)
                .allMatch(w -> w.getY() + w.getHeight() <= screen.height),
            "All manager controls fit the current GUI height");
        net.minecraft.client.Screenshot.grab(
            mc.gameDirectory, "typed-planet-manager.png", mc.getMainRenderTarget(), m -> {});
        mc.setScreen(null);
        command("planet generate tcp_jungle_090 64 jungle 888 false");
        command("planet enter tcp_jungle_090");
      }
      if (tick == 1970) {
        check(
            PlanetClient.active() && PlanetClient.planet().radius() == 64,
            "TCP creates native jungle planet");
        check(
            mc.level
                .getBiome(mc.player.blockPosition())
                .is(net.minecraft.world.level.biome.Biomes.JUNGLE),
            "Native jungle biome reaches the client");
        command("planet fly");
        command("tp @s 0.5 160 0.5 0 90");
      }
      if (tick == 2050) {
        check(
            PeriodicRenderer.drawn > 0, "Native jungle terrain renders with the planet projection");
        net.minecraft.client.Screenshot.grab(
            mc.gameDirectory, "typed-jungle-planet.png", mc.getMainRenderTarget(), m -> {});
        command("planet leave");
      }
      if (tick == 2110) {
        check(!PlanetClient.active(), "TCP leaves native preset safely");
        Files.write(mc.gameDirectory.toPath().resolve("periodic-network-results.txt"), results);
        SpaceBlocks.LOGGER.info("PERIODIC_NETWORK_TEST_PASS");
        command("save-all flush");
      }
      if (tick == 2150) {
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
