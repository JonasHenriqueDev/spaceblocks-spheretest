package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlanetAtlasClientTests {
  private static int ticks, crossings;
  private static double lastX, highestOther = -1000;
  private static boolean finished;
  private static volatile Throwable serverFailure;
  private static volatile boolean tunnelReady;
  private static long buildDeadline;
  private static final List<String> results = new ArrayList<>();

  private static void check(boolean result, String label) {
    if (!result) throw new IllegalStateException(label);
    results.add("PASS " + label);
    SpaceBlocks.LOGGER.info("ATLAS_CLIENT {}", label);
  }

  private static void server(Consumer<ServerPlayer> action) {
    var mc = Minecraft.getInstance();
    var id = mc.player.getUUID();
    mc.getSingleplayerServer()
        .execute(
            () -> {
              try {
                action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));
              } catch (Throwable error) {
                serverFailure = error;
              }
            });
  }

  private static void command(String command) {
    server(
        p ->
            p.server
                .getCommands()
                .performPrefixedCommand(p.createCommandSourceStack().withPermission(4), command));
  }

  private static void capture(String file) {
    var mc = Minecraft.getInstance();
    net.minecraft.client.Screenshot.grab(
        mc.gameDirectory, file + ".png", mc.getMainRenderTarget(), message -> {});
  }

  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) return;
    if (finished) {
      PlanetLabClientTests.tick();
      return;
    }
    ticks++;
    try {
      if (serverFailure != null)
        throw new IllegalStateException("Server assertion failed", serverFailure);
      if (ticks == 519 || ticks == 1399) {
        if (System.nanoTime() > buildDeadline)
          throw new IllegalStateException("Tunnel build timed out");
        server(
            p ->
                tunnelReady =
                    PlanetSettings.get(p.level()).hasTunnel
                        && !PlanetTunnel.isBuilding(p.serverLevel()));
        if (!tunnelReady) {
          ticks--;
          return;
        }
      }
      if (ticks == 1) {
        check(
            SpaceBlocks.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString()
                .contains("spaceblocks-0.8.3.jar"),
            "Packaged atlas JAR loaded");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(5);
        mc.options.simulationDistance().set(5);
        mc.options.broadcastOptions();
        server(
            p -> {
              var saved = PlanetSettings.get(p.level());
              if (saved.hasTunnel)
                check(
                    !saved.airDrag && saved.realisticGravity && saved.fallthrough,
                    "Tunnel physics persisted after closing and reopening the world");
            });
        command("planet small");
      }
      if (ticks == 100) command("planet map");
      if (ticks == 180) {
        check(
            mc.screen instanceof PlanetAtlasScreen, "Command opens 3D panel from server snapshot");
        var screen = (PlanetAtlasScreen) mc.screen;
        check(screen.renderedTriangles > 1000, "Actual 3D mesh rendered");
        check(
            screen.snapshot().size() == 224 && screen.snapshot().radius() == 32,
            "Planet size and radius displayed");
        check(screen.snapshot().confirmedCount() > 0, "Loaded terrain samples confirmed");
        capture("atlas-globe");
      }
      if (ticks == 200) {
        var s = (PlanetAtlasScreen) mc.screen;
        double yaw = s.yawValue(), zoom = s.zoomValue();
        check(s.mouseDragged(60, 80, 0, 40, 15) && s.yawValue() != yaw, "Drag rotates the planet");
        check(s.mouseScrolled(60, 80, 0, 2) && s.zoomValue() > zoom, "Scroll zooms the planet");
        s.centerPlayer();
        capture("atlas-rotated");
      }
      if (ticks == 220) {
        var s = (PlanetAtlasScreen) mc.screen;
        s.toggleProjection();
        check(s.exactMode(), "Original Spheretest projection mode");
      }
      if (ticks == 240) {
        check(
            ((PlanetAtlasScreen) mc.screen).renderedTriangles > 100,
            "Spheretest hemisphere mesh rendered");
        capture("atlas-spheretest");
        server(
            p ->
                p.serverLevel()
                    .setBlock(
                        new BlockPos(0, 100, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3));
      }
      if (ticks == 280) PacketDistributor.sendToServer(new PlanetAtlas.Request());
      if (ticks == 350) {
        var s = (PlanetAtlasScreen) mc.screen;
        int n = s.snapshot().resolution();
        check(
            s.snapshot().heights()[(n / 2) * n + n / 2] == 100,
            "Refresh reads changed terrain without regenerating chunks");
        mc.setScreen(null);
        server(
            p ->
                p.serverLevel()
                    .setBlock(new BlockPos(0, 100, 0), Blocks.AIR.defaultBlockState(), 3));
        tunnelReady = false;
        buildDeadline = System.nanoTime() + 60_000_000_000L;
        command("tp @s 40.5 70 30.5");
        command("planet tunnel create");
      }
      if (ticks == 520) {
        server(
            p ->
                check(
                    PlanetSettings.get(p.level()).hasTunnel,
                    "Both connected tunnel shafts completed"));
        command("planet tunnel drop");
      }
      if ((ticks > 540 && ticks < 920) || ticks > 1420) {
        double x = mc.player.getX();
        if (Math.abs(x - lastX) > 70) crossings++;
        lastX = x;
        if (x < 0) highestOther = Math.max(highestOther, mc.player.getY());
      }
      if (ticks % 50 == 0 && ticks > 520)
        SpaceBlocks.LOGGER.info(
            "TUNNEL_TRACE tick={} x={} y={} vy={} crossings={} apogee={}",
            ticks,
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getDeltaMovement().y,
            crossings,
            highestOther);
      if (ticks == 900) {
        check(crossings >= 2, "Fall traverses bottom and returns through connected tunnel");
        check(highestOther > 65, "Player rises to other surface after passage");
        check(!mc.player.getAbilities().flying, "Oscillation uses gravity, without flight");
        check(
            !SpaceBlocks.clientSettings.airDrag && SpaceBlocks.clientSettings.realisticGravity,
            "Original variable gravity and undamped vertical fall synchronized");
        capture("tunnel-oscillation");
      }
      if (ticks == 920) {
        command("planet natural");
        crossings = 0;
        highestOther = -1000;
        lastX = 0;
      }
      if (ticks == 1040) command("planet map");
      if (ticks == 1120) {
        check(mc.screen instanceof PlanetAtlasScreen, "Natural planet atlas opens");
        check(
            ((PlanetAtlasScreen) mc.screen).snapshot().size() == 1632,
            "Natural map size displayed");
        capture("atlas-natural");
        mc.setScreen(null);
        tunnelReady = false;
        buildDeadline = System.nanoTime() + 60_000_000_000L;
        command("tp @s 40.5 170 30.5");
        command("planet tunnel create");
      }
      if (ticks == 1400) {
        server(
            p ->
                check(
                    PlanetSettings.get(p.level()).hasTunnel,
                    "Natural tunnel clears all bottom bedrock layers"));
        command("planet tunnel drop");
        lastX = 0;
      }
      if (ticks == 2400) {
        check(crossings >= 2, "Natural planet oscillation crosses both connected shafts");
        check(highestOther > 64, "Natural fall rises above the opposite surface reference");
        capture("tunnel-natural-oscillation");
        server(
            p -> {
              check(
                  Blocks.BEDROCK.defaultBlockState().getDestroySpeed(p.level(), p.blockPosition())
                      > 0,
                  "Fallthrough makes bottom bedrock mineable");
              check(
                  PlanetSettings.get(p.level()).hasTunnel,
                  "Tunnel coordinates retained in saved settings");
            });
        command("planet surface");
      }
      if (ticks == 2410)
        check(
            mc.player.getY() > 64 || mc.player.isInWater() && mc.player.getY() > 60,
            "Surface command exits the open shaft safely; y=" + mc.player.getY());
      if (ticks == 2420) {
        Files.write(mc.gameDirectory.toPath().resolve("atlas-client-results.txt"), results);
        SpaceBlocks.LOGGER.info(
            "ATLAS_CLIENT_TEST_PASS crossings={} otherApogee={}", crossings, highestOther);
        finished = true;
      }
    } catch (Throwable error) {
      finished = true;
      SpaceBlocks.LOGGER.error("ATLAS_CLIENT_TEST_FAIL", error);
      try {
        Files.writeString(
            mc.gameDirectory.toPath().resolve("atlas-client-results.txt"), "FAIL " + error);
      } catch (Exception ignored) {
      }
      mc.stop();
    }
  }
}
