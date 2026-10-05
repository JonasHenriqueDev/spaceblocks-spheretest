package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Opt-in packaged artifact integration checks; only the separate run-hud world is modified. */
public final class PlanetHudClientTests {
  private static int ticks;
  private static boolean done;
  private static volatile Throwable error;
  private static float maxRoll;
  private static boolean capturedRoll;
  private static final List<String> results = new ArrayList<>();

  private static void check(boolean ok, String label) {
    if (!ok) throw new IllegalStateException(label);
    results.add("PASS " + label);
    SpaceBlocks.LOGGER.info("HUD_CLIENT {}", label);
  }

  private static void server(Consumer<ServerPlayer> task) {
    var mc = Minecraft.getInstance();
    var id = mc.player.getUUID();
    mc.getSingleplayerServer()
        .execute(
            () -> {
              try {
                task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));
              } catch (Throwable e) {
                error = e;
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

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
  }

  public static void tick() {
    var mc = Minecraft.getInstance();
    if (done || mc.player == null || mc.level == null) return;
    ticks++;
    try {
      if (error != null) throw new IllegalStateException(error);
      if (ticks == 1) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) ->
                maxRoll = Math.max(maxRoll, Math.abs(event.getRoll())));
        check(
            SpaceBlocks.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString()
                .contains("spaceblocks-0.8.3.jar"),
            "Packaged 0.8.3 loaded");
        mc.options.pauseOnLostFocus = false;
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        mc.options.renderDistance().set(5);
        mc.options.simulationDistance().set(5);
        mc.options.broadcastOptions();
        mc.setScreen(null);
        PlanetHudConfig.load();
        PlanetHudConfig.enabled = true;
        PlanetHudConfig.size = 128;
        PlanetHudConfig.corner = 0;
        PlanetHudConfig.transition = true;
        PlanetHudConfig.duration = 1200;
        PlanetHudConfig.save();
        command("planet small");
      }
      if (ticks == 60) command("planet tunnel axis");
      if (ticks == 220) {
        check(PlanetHud.renderedTriangles > 200, "HUD draws sampled 3D globe");
        check(
            PlanetHud.hasAtlas() && mc.screen == null,
            "Passive atlas packet arrives without opening a screen");
        check(PlanetHud.renderedMarker, "HUD shows the live player marker");
        server(
            p -> {
              var s = PlanetSettings.get(p.level());
              var planet = Planet.of(p.level());
              check(
                  s.hasTunnel && !PlanetTunnel.isBuilding(p.serverLevel()),
                  "Marked physical tunnel completed");
              check(
                  p.level()
                      .getBlockState(new BlockPos(s.tunnelX + 2, planet.bottom() + 8, s.tunnelZ))
                      .is(Blocks.CYAN_STAINED_GLASS),
                  "Entrance has cyan measurement ring");
              int other = PeriodicMath.wrap(s.tunnelX + planet.size() / 2, planet.size());
              check(
                  p.level()
                      .getBlockState(new BlockPos(other + 2, planet.bottom() + 8, s.tunnelZ))
                      .is(Blocks.MAGENTA_STAINED_GLASS),
                  "Exit has magenta measurement ring");
              check(
                  p.level()
                      .getBlockState(new BlockPos(other, planet.bottom() + 2, s.tunnelZ))
                      .isAir(),
                  "Opposite tunnel center stays passable");
              p.getAbilities().mayfly = true;
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.connection.teleport(s.tunnelX + .5, 90, s.tunnelZ + .5, 0, 90);
              p.setDeltaMovement(Vec3.ZERO);
            });
        capture("hud-top-right");
      }
      if (ticks == 260) {
        check(
            net.neoforged.neoforge.client.ClientCommandHandler.runCommand("planet hud"),
            "HUD command executes locally without operator requirement");
      }
      if (ticks == 270) {
        check(
            mc.screen instanceof PlanetHudScreen && mc.screen.children().size() == 8,
            "Mod menu exposes all eight HUD controls");
        capture("hud-settings");
        ((net.minecraft.client.gui.components.Button) mc.screen.children().get(1)).onPress();
        check(PlanetHudConfig.size == 144, "Size button changes the HUD and saves its preference");
        ((net.minecraft.client.gui.components.Button) mc.screen.children().get(2)).onPress();
        check(PlanetHudConfig.corner == 1, "Position button selects top left");
      }
      if (ticks == 280) {
        mc.setScreen(null);
        PlanetHudConfig.corner = 3;
        PlanetHudConfig.size = 96;
        PlanetHudConfig.save();
      }
      if (ticks == 300) {
        capture("hud-bottom-left");
        command("planet tunnel drop");
      }
      if (ticks > 300 && !capturedRoll && maxRoll > 120) {
        capturedRoll = true;
        capture("hud-camera-inversion");
      }
      if (ticks == 440) {
        check(PlanetHud.crossingCount() > 0, "Actual bottom crossing triggers camera cue");
        check(maxRoll > 170, "Camera event actually renders the smooth inversion near 180 degrees");
        check(PlanetHud.renderedTriangles > 200, "HUD remains functional after crossing");
        check(
            Math.abs(
                    PlanetClient.planet().size() / 2.0 / PlanetClient.planet().projectionRadius()
                        - Math.PI)
                < 1e-12,
            "Client half-map projects to 180 degrees");
        server(
            p -> {
              check(
                  p.level().noCollision(p), "Player remains clear of blocks after tunnel crossing");
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.setDeltaMovement(Vec3.ZERO);
            });
        capture("hud-crossed");
      }
      if (ticks == 460) {
        PlanetHudConfig.corner = 0;
        PlanetHudConfig.size = 96;
        PlanetHudConfig.save();
        command("planet leave");
      }
      if (ticks == 480) {
        check(
            !PlanetClient.active(),
            "Leave command still returns to standard world with client command registered");
        Files.write(mc.gameDirectory.toPath().resolve("hud-client-results.txt"), results);
        done = true;
        mc.stop();
      }
    } catch (Throwable e) {
      done = true;
      SpaceBlocks.LOGGER.error("HUD_CLIENT_TEST_FAIL", e);
      try {
        Files.writeString(mc.gameDirectory.toPath().resolve("hud-client-results.txt"), "FAIL " + e);
      } catch (Exception ignored) {
      }
      mc.stop();
    }
  }
}
