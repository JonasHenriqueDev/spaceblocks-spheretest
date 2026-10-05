package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.*;

public final class PlanetClientTests {
  private static int ticks;
  private static boolean failed;
  private static double deepest = Double.POSITIVE_INFINITY;
  private static int mined;
  private static boolean deepStopped;
  private static volatile Vec3 netherPortal;
  private static final List<String> results = new ArrayList<>();

  private static void check(boolean ok, String message) {
    if (!ok) throw new IllegalStateException(message);
    results.add("PASS " + message);
    SpaceBlocks.LOGGER.info("PERIODIC_CLIENT: {}", message);
  }

  private static void server(Consumer<ServerPlayer> action) {
    var mc = Minecraft.getInstance();
    var s = mc.getSingleplayerServer();
    var id = mc.player.getUUID();
    s.execute(
        () -> {
          try {
            var p = s.getPlayerList().getPlayer(id);
            if (p != null) action.accept(p);
          } catch (Throwable ex) {
            fail(ex);
          }
        });
  }

  private static void command(String text) {
    server(
        p ->
            p.server
                .getCommands()
                .performPrefixedCommand(p.createCommandSourceStack().withPermission(4), text));
  }

  private static void teleport(double x, double y, double z, float yaw, float pitch) {
    server(
        p -> {
          p.connection.teleport(x, y, z, yaw, pitch);
          p.setDeltaMovement(Vec3.ZERO);
          p.fallDistance = 0;
        });
  }

  private static void resetShaft() {
    server(
        p -> {
          var level = p.serverLevel();
          var generator = (PlanetGenerator) level.getChunkSource().getGenerator();
          for (int y = generator.planet.bottom() + 1; y <= 64; y++)
            for (int x = 49; x <= 51; x++)
              for (int z = 49; z <= 51; z++)
                level.setBlock(new BlockPos(x, y, z), generator.block(y), 3);
        });
  }

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        name + ".png",
        mc.getMainRenderTarget(),
        m -> SpaceBlocks.LOGGER.info("PERIODIC_CAPTURE {}", name));
  }

  private static void fail(Throwable ex) {
    failed = true;
    SpaceBlocks.LOGGER.error("PERIODIC_CLIENT_TEST_FAIL", ex);
    try {
      Files.writeString(
          Minecraft.getInstance().gameDirectory.toPath().resolve("periodic-client-results.txt"),
          "FAIL: " + ex);
    } catch (Exception ignored) {
    }
    Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
  }

  public static void tick(ClientTickEvent.Post e) {
    if (Boolean.getBoolean("spaceblocks.hudClient")) {
      PlanetHudClientTests.tick();
      return;
    }
    if (Boolean.getBoolean("spaceblocks.bottomClient")) {
      PlanetBottomClientTests.tick();
      return;
    }
    if (Boolean.getBoolean("spaceblocks.labClient")) {
      PlanetLabClientTests.tick();
      return;
    }
    if (Boolean.getBoolean("spaceblocks.atlasClient")) {
      PlanetAtlasClientTests.tick(e);
      return;
    }
    if (Boolean.getBoolean("spaceblocks.performanceClient")) {
      PlanetPerformanceTests.tick(e);
      return;
    }
    if (!Boolean.getBoolean("spaceblocks.testClient") || failed) return;
    var mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) return;
    ticks++;
    try {
      if (ticks == 1) {
        if (Boolean.getBoolean("spaceblocks.packagedTest"))
          check(
              SpaceBlocks.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toString()
                  .contains("spaceblocks-0.8.3.jar"),
              "Loaded final packaged JAR, not development classes");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(5);
        mc.options.simulationDistance().set(5);
        mc.setScreen(null);
      }
      if (ticks == 30) command("planet small");
      if (ticks == 100) {
        check(PlanetClient.planet() == Planet.SMALL, "Small planet command and dimension");
        server(
            p -> {
              var l = p.serverLevel();
              // Reset only harness fixtures so reruns still test genuine collision.
              for (int x = -112; x <= -109; x++)
                for (int y = 65; y <= 69; y++)
                  l.setBlock(new BlockPos(x, y, 20), Blocks.AIR.defaultBlockState(), 3);
              for (int y = 65; y <= 80; y++)
                l.setBlock(new BlockPos(8, y, 0), Blocks.STONE_BRICKS.defaultBlockState(), 3);
              for (int y : new int[] {81, 96, 112}) {
                for (int x = 12; x < 15; x++)
                  l.setBlock(new BlockPos(x, y - 1, 4), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                l.setBlock(new BlockPos(13, y, 4), Blocks.TNT.defaultBlockState(), 3);
              }
              l.setBlock(new BlockPos(8, 81, 0), Blocks.TNT.defaultBlockState(), 3);
              for (int x = -3; x <= 3; x++)
                for (int z = 5; z <= 11; z++) {
                  l.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
                  l.setBlock(new BlockPos(x, 65, z), Blocks.WATER.defaultBlockState(), 3);
                }
              l.setBlock(new BlockPos(-4, 65, 5), Blocks.CHEST.defaultBlockState(), 3);
              var pig = EntityType.PIG.create(l);
              pig.setPos(4.5, 65, 4.5);
              pig.setNoAi(true);
              l.addFreshEntity(pig);
              p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_BLOCK, 64));
              p.inventoryMenu.broadcastChanges();
            });
      }
      if (ticks == 150) teleport(.5, 65, -12.5, 0, 10);
      if (ticks == 240) {
        check(
            PeriodicRenderer.compiled > 0 && PeriodicRenderer.drawn > 0,
            "Actual terrain VBO compilation and drawing");
        capture("periodic-small-ground-water-entities");
      }
      if (ticks == 260) {
        command("planet fly");
        teleport(8.5, 80, -3.5, 0, 0);
      }
      if (ticks == 320) {
        check(
            mc.hitResult instanceof BlockHitResult h
                && mc.level.getBlockState(h.getBlockPos()).is(Blocks.TNT),
            "Projected TNT selection in client");
        capture("periodic-tnt-selection");
        var h = (BlockHitResult) mc.hitResult;
        mc.gameMode.startDestroyBlock(h.getBlockPos(), h.getDirection());
      }
      if (ticks == 360) {
        check(
            mc.level.getBlockState(new BlockPos(8, 81, 0)).isAir(),
            "Actual client mining packet and server block update");
        mc.player.getInventory().selected = 0;
        mc.gameMode.useItemOn(
            mc.player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(new Vec3(8.5, 81, .5), Direction.UP, new BlockPos(8, 80, 0), false));
      }
      if (ticks == 410) {
        check(
            mc.level.getBlockState(new BlockPos(8, 81, 0)).is(Blocks.DIAMOND_BLOCK),
            "Actual client construction packet and server block update");
        teleport(6.5, 86, -10.5, 0, 12);
      }
      if (ticks == 450) capture("periodic-tnt-towers");
      if (ticks == 470) {
        command("planet walk");
        teleport(110.5, 65, 20.5, -90, 0);
      }
      if (ticks == 490) mc.options.keyUp.setDown(true);
      if (ticks == 535) {
        mc.options.keyUp.setDown(false);
        check(mc.player.getX() < -90, "W movement across east seam");
        check(Math.abs(mc.player.getYRot() + 90) < .01, "Yaw unchanged after east seam");
        teleport(-110.5, 65, 20.5, 90, 0);
      }
      if (ticks == 555) mc.options.keyUp.setDown(true);
      if (ticks == 600) {
        mc.options.keyUp.setDown(false);
        check(mc.player.getX() > 90, "W movement across west seam");
        teleport(20.5, 65, -110.5, 180, 0);
      }
      if (ticks == 620) mc.options.keyUp.setDown(true);
      if (ticks == 665) {
        mc.options.keyUp.setDown(false);
        check(mc.player.getZ() > 90, "W movement across north seam");
        teleport(20.5, 65, 110.5, 0, 0);
      }
      if (ticks == 685) mc.options.keyUp.setDown(true);
      if (ticks == 730) {
        mc.options.keyUp.setDown(false);
        check(mc.player.getZ() < -90, "W movement across south seam");
        teleport(110.5, 65, 110.5, -45, 0);
      }
      if (ticks == 750) mc.options.keyUp.setDown(true);
      if (ticks == 800) {
        mc.options.keyUp.setDown(false);
        check(
            mc.player.getX() < -90 && mc.player.getZ() < -90,
            "W diagonal movement across both seams");
        command("planet fly");
        teleport(.5, 120, -12.5, 0, 65);
      }
      if (ticks == 860) capture("periodic-small-altitude");
      if (ticks == 880) {
        teleport(108.5, 70, 20.5, -90, 30);
        server(
            p -> {
              var l = p.serverLevel();
              for (int x = -112; x <= -109; x++)
                for (int y = 65; y <= 69; y++)
                  l.setBlock(new BlockPos(x, y, 20), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            });
      }
      if (ticks == 940) {
        capture("periodic-seam-construction");
        check(
            mc.level.getBlockState(new BlockPos(112, 68, 20)).is(Blocks.GOLD_BLOCK),
            "Opposite-edge construction visible through canonical client read");
      }
      if (ticks == 960) {
        resetShaft();
        command("planet walk");
        command("planet physics fallthrough false");
        teleport(50.5, 66, 50.5, 0, 90);
        deepest = 66;
        mined = 0;
      }
      if (ticks >= 985 && ticks < 1110) {
        deepest = Math.min(deepest, mc.player.getY());
        if (mc.hitResult instanceof BlockHitResult h && h.getType() != HitResult.Type.MISS) {
          mc.gameMode.startDestroyBlock(h.getBlockPos(), h.getDirection());
          mined++;
        }
      }
      if (ticks == 1110) {
        check(
            deepest < 45 && mined > 20, "Client mining downward through small core without crash");
        command("planet small");
        command("planet physics fallthrough true");
      }
      if (ticks == 1200) {
        teleport(110.5, 65, 24.5, -90, 20);
        server(
            p -> {
              var pig = EntityType.PIG.create(p.serverLevel());
              pig.setNoAi(true);
              pig.setPos(-110.5, 65, 24.5);
              p.serverLevel().addFreshEntity(pig);
            });
      }
      if (ticks == 1260) {
        check(
            mc.hitResult instanceof EntityHitResult,
            "Projected entity selection across connected edge");
        capture("periodic-entity-at-seam");
        mc.gameMode.attack(mc.player, ((EntityHitResult) mc.hitResult).getEntity());
      }
      if (ticks == 1310)
        server(
            p ->
                check(
                    p
                        .serverLevel()
                        .getEntitiesOfClass(
                            net.minecraft.world.entity.animal.Pig.class,
                            new AABB(-111, 64, 24, -110, 67, 25))
                        .stream()
                        .anyMatch(pig -> pig.getHealth() < pig.getMaxHealth()),
                    "Actual entity interaction packet across edge"));
      if (ticks == 1360) command("planet flat");
      if (ticks == 1480) {
        check(PlanetClient.planet() == Planet.LARGE, "Large planet command and dimension");
        command("planet fly");
        teleport(.5, 130, -20.5, 0, 40);
      }
      if (ticks == 1560) {
        check(PeriodicRenderer.drawn > 0, "Large planet projected render");
        capture("periodic-large-altitude");
        command("planet physics realistic_gravity true");
        command("planet physics centrifugal false");
        command("planet physics fallthrough false");
      }
      if (ticks == 1600) {
        resetShaft();
        check(
            SpaceBlocks.clientSettings.realisticGravity
                && !SpaceBlocks.clientSettings.centrifugal
                && !SpaceBlocks.clientSettings.fallthrough,
            "Physics option packets synchronized");
        command("planet physics realistic_gravity false");
        command("planet physics centrifugal true");
        command("planet physics fallthrough true");
        command("planet walk");
        teleport(50.5, 66, 50.5, 0, 90);
        deepest = 66;
        mined = 0;
        deepStopped = false;
      }
      if (ticks >= 1640 && ticks < 1940 && !deepStopped) {
        deepest = Math.min(deepest, mc.player.getY());
        if (mc.hitResult instanceof BlockHitResult h && h.getType() != HitResult.Type.MISS) {
          mc.gameMode.startDestroyBlock(h.getBlockPos(), h.getDirection());
          mined++;
        }
        if (deepest < -182) {
          deepStopped = true;
          command("planet fly");
        }
      }
      if (ticks == 1960) {
        check(deepest < -150 && mined > 200, "Client deep mining in large planet without crash");
        capture("periodic-large-deep-mining");
        command("planet fly");
        server(
            p -> {
              var d = Planet.of(p.level());
              int opposite = PeriodicMath.wrap(d.size() / 2, d.size());
              for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                  for (int y = d.bottom(); y <= d.bottom() + 4; y++)
                    p.level()
                        .setBlock(
                            new BlockPos(opposite + x, y, z), Blocks.AIR.defaultBlockState(), 3);
            });
        teleport(.5, -193, .5, 0, 0);
      }
      if (ticks == 2020) {
        check(
            mc.player.getY() >= -192 && Math.abs(mc.player.getX()) > 800,
            "Client/server fallthrough and half-map horizontal shift");
        command("planet leave");
      }
      if (ticks == 2060) {
        check(PlanetClient.planet() == null, "Return command restores standard world");
        command("planet natural");
      }
      if (ticks == 2220) {
        check(mc.level.dimension().equals(SpaceBlocks.NATURAL), "Natural planet client entry");
        check(
            !SpaceBlocks.clientSettings.fallthrough,
            "Natural core exploration has no automatic bottom teleport");
        command("planet fly");
        teleport(0.5, 180, 0.5, 0, 70);
      }
      if (ticks == 2280) {
        capture("periodic-natural-relief");
        check(PeriodicRenderer.drawn > 0, "Natural terrain rendered");
        command("planet core");
      }
      if (ticks == 2400) {
        check(
            mc.player.getY() < -460 && mc.player.getAbilities().flying,
            "Deep core chamber accessible: y="
                + mc.player.getY()
                + ", flying="
                + mc.player.getAbilities().flying);
        capture("periodic-natural-core");
        command("planet noclip true");
      }
      if (ticks == 2460) {
        check(mc.player.isSpectator(), "Noclip enables collision-free interior exploration");
        teleport(10.5, -300, 10.5, 0, 0);
      }
      if (ticks == 2520) {
        check(mc.player.getY() < -290, "Noclip occupies deep terrain without collision");
        command("planet noclip false");
      }
      if (ticks == 2600) {
        check(
            !mc.player.isSpectator() && mc.player.getY() > 40,
            "Noclip off safely returns to surface and restores game mode");
        command("planet leave");
      }
      if (ticks == 2640) {
        check(PlanetClient.planet() == null, "Natural planet leave restores standard world");
        command("planet flat");
      }
      if (ticks == 2740)
        server(
            p -> {
              var boat = EntityType.BOAT.create(p.level());
              boat.setNoGravity(true);
              boat.moveTo(814.5, 100, 50.5, -90, 0);
              p.serverLevel().addFreshEntity(boat);
              p.connection.teleport(814.5, 100, 50.5, -90, 0);
              p.startRiding(boat, true);
            });
      if (ticks == 2820) {
        check(mc.player.isPassenger(), "Client mounted boat");
        mc.options.keyUp.setDown(true);
      }
      if (ticks == 2920) {
        mc.options.keyUp.setDown(false);
        check(
            mc.player.isPassenger() && mc.player.getRootVehicle().getX() < 0,
            "Player-controlled boat crosses seam without dismounting");
        capture("periodic-boat-seam");
        server(p -> p.stopRiding());
        command("planet leave");
      }
      if (ticks == 2980) {
        command("planet natural");
      }
      if (ticks == 3100)
        server(
            p -> {
              var rectangle =
                  p.serverLevel()
                      .getPortalForcer()
                      .createPortal(new BlockPos(20, 150, 20), Direction.Axis.X)
                      .orElseThrow();
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.connection.teleport(
                  rectangle.minCorner.getX() + .5,
                  rectangle.minCorner.getY() + .1,
                  rectangle.minCorner.getZ() + .5,
                  0,
                  0);
            });
      if (ticks == 3260) {
        check(
            mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER),
            "Actual Nether portal from natural planet");
        server(
            p -> {
              netherPortal = p.position();
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.connection.teleport(p.getX() + 8, p.getY() + 8, p.getZ(), 0, 0);
            });
      }
      if (ticks == 3620)
        server(p -> p.connection.teleport(netherPortal.x, netherPortal.y, netherPortal.z, 0, 0));
      if (ticks == 3780) {
        check(
            mc.level.dimension().equals(SpaceBlocks.NATURAL),
            "Actual Nether portal returns to originating planet");
        capture("periodic-nether-return");
        command("planet leave");
      }
      if (ticks == 3860) {
        Files.write(mc.gameDirectory.toPath().resolve("periodic-client-results.txt"), results);
        SpaceBlocks.LOGGER.info("PERIODIC_CLIENT_TEST_PASS");
        mc.stop();
      }
      if (ticks > 4200) throw new IllegalStateException("Client harness timeout");
    } catch (Throwable ex) {
      fail(ex);
    }
  }

  private static long lastStartup;

  public static void frame(RenderFrameEvent.Post e) {
    if (!Boolean.getBoolean("spaceblocks.testClient")) return;
    var mc = Minecraft.getInstance();
    if (mc.player == null && mc.screen != null && System.currentTimeMillis() - lastStartup > 5000) {
      lastStartup = System.currentTimeMillis();
      SpaceBlocks.LOGGER.info("PERIODIC_START_SCREEN {}", mc.screen.getClass().getName());
      if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
        mc.screen.onClose();
        return;
      }
      if (mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
        mc.createWorldOpenFlows()
            .openWorld(
                "PeriodicTest",
                () -> fail(new IllegalStateException("Unable to open isolated test world")));
        return;
      }
      if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
        for (var child : mc.screen.children())
          if (child instanceof net.minecraft.client.gui.components.Button button
              && button
                  .getMessage()
                  .getString()
                  .equals(
                      net.minecraft.network.chat.Component.translatable(
                              "selectWorld.backupJoinSkipButton")
                          .getString())) {
            button.onPress();
            break;
          }
      }
    }
  }
}
