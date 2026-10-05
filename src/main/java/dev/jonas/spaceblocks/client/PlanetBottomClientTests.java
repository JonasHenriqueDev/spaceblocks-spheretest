package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Reproduces a hand-dug local shaft facing a completely solid opposite exit. */
public final class PlanetBottomClientTests {
  private static int ticks;
  private static boolean done;
  private static boolean naturalCrossed;
  private static boolean smallCrossed;
  private static long renderDeadline;
  private static volatile Throwable error;
  private static final List<String> results = new ArrayList<>();

  private static void check(boolean ok, String label) {
    if (!ok) throw new IllegalStateException(label);
    results.add("PASS " + label);
    SpaceBlocks.LOGGER.info("BOTTOM_CLIENT {}", label);
  }

  private static void server(Consumer<ServerPlayer> task) {
    var mc = Minecraft.getInstance();
    var id = mc.player.getUUID();
    mc.getSingleplayerServer()
        .execute(
            () -> {
              try {
                task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));
              } catch (Throwable failure) {
                error = failure;
              }
            });
  }

  public static void tick() {
    var mc = Minecraft.getInstance();
    if (done || mc.player == null || mc.level == null) return;
    ticks++;
    try {
      if (error != null) throw new IllegalStateException("Server assertion", error);
      if (ticks == 1) {
        check(
            SpaceBlocks.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString()
                .contains("spaceblocks-0.9.0.jar"),
            "Final packaged bottom-passage JAR loaded");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(5);
        mc.options.simulationDistance().set(5);
        mc.options.broadcastOptions();
        mc.setScreen(null);
        server(
            p ->
                p.server
                    .getCommands()
                    .performPrefixedCommand(
                        p.createCommandSourceStack().withPermission(4), "planet small"));
      }
      if (ticks == 40)
        server(
            p -> {
              var level = p.serverLevel();
              int b = Planet.of(level).bottom();
              var s = PlanetSettings.get(level);
              s.fallthrough = true;
              s.realisticGravity = false;
              s.centrifugal = false;
              s.setDirty();
              PlanetNetwork.sync(p);
              for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                  for (int y = b - 4; y <= 66; y++)
                    level.setBlock(
                        new BlockPos(40 + dx, y, 30 + dz), Blocks.AIR.defaultBlockState(), 3);
                  for (int y = b; y <= b + 5; y++)
                    level.setBlock(
                        new BlockPos(-72 + dx, y, 30 + dz),
                        Blocks.GLOWSTONE.defaultBlockState(),
                        3);
                }
              p.setGameMode(GameType.SURVIVAL);
              p.getAbilities().mayfly = true;
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
              p.inventoryMenu.broadcastChanges();
              p.connection.teleport(40.5, b + .2, 30.5, 0, 90);
              level.setBlock(new BlockPos(38, b + 2, 30), Blocks.GLOWSTONE.defaultBlockState(), 3);
              p.setDeltaMovement(Vec3.ZERO);
              PlanetServer.reset(p);
            });
      if (ticks == 160) {
        var hit = PlanetClient.pickScene(mc.player, mc.player.blockInteractionRange(), 0, 1);
        check(
            hit instanceof BlockHitResult h
                && h.getType() == HitResult.Type.BLOCK
                && h.getBlockPos().equals(new BlockPos(-72, 32, 30)),
            "Closed opposite exit selected through bottom plane");
        check(
            PeriodicRenderer.bottomDrawn > 0, "Opposite terrain is actually drawn near the bottom");
        net.minecraft.client.Screenshot.grab(
            mc.gameDirectory, "bottom-solid-exit.png", mc.getMainRenderTarget(), message -> {});
        server(
            p -> {
              check(
                  p.canInteractWithBlock(new BlockPos(-72, 32, 30), 0),
                  "Server accepts nearby bottom block within survival reach");
              check(
                  !p.canInteractWithBlock(new BlockPos(-72, 50, 30), 0),
                  "Server rejects distant blocks through bottom passage");
              p.setPos(40.5, 31.9, 30.5);
              p.setDeltaMovement(0, -.2, 0);
              PlanetServer.wrap(p);
              check(
                  Math.abs(p.getX() - 40.5) < .001 && p.getY() >= 32,
                  "Blocked exit prevents teleport into solid terrain");
              p.getAbilities().flying = false;
              p.onUpdateAbilities();
              p.setDeltaMovement(0, -.1, 0);
            });
      }
      if (ticks == 175)
        server(
            p -> {
              check(
                  Math.abs(p.getX() - 40.5) < .001 && p.onGround(),
                  "Closed exit provides stable ground while the player mines");
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.setDeltaMovement(Vec3.ZERO);
            });
      if (ticks >= 180 && ticks < 390) {
        var hit = PlanetClient.pickScene(mc.player, mc.player.blockInteractionRange(), 0, 1);
        if (hit instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK)
          mc.gameMode.continueDestroyBlock(h.getBlockPos(), h.getDirection());
      }
      if (ticks == 390) {
        mc.gameMode.stopDestroyBlock();
        server(
            p -> {
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.connection.teleport(40.5, 32.2, 30.5, 0, 90);
              p.setDeltaMovement(Vec3.ZERO);
            });
      }
      if (ticks == 399)
        check(mc.player.getXRot() > 85, "Controlled crossing starts with downward look");
      if (ticks == 400) {
        mc.gameMode.stopDestroyBlock();
        server(
            p -> {
              for (int y = 32; y <= 34; y++)
                check(
                    p.level().getBlockState(new BlockPos(-72, y, 30)).isAir(),
                    "Survival mining packet removed opposite block Y=" + y);
              p.setPos(40.5, 31.9, 30.5);
              p.setDeltaMovement(0, -.2, 0);
              PlanetServer.wrap(p);
              check(
                  Math.abs(p.getX() + 71.5) < .001 && p.getY() == 33,
                  "Manually mined exit permits half-map passage");
              check(
                  p.level().noCollision(p),
                  "Player emerges without suffocation or embedded collision");
            });
      }
      if (ticks == 440) {
        check(smallCrossed, "Client receives manual-tunnel crossing");
        check(mc.player.getXRot() < -85, "Bottom crossing reflects downward look upward");
        server(
            p -> {
              var s = PlanetSettings.get(p.level());
              s.fallthrough = false;
              s.setDirty();
              PlanetNetwork.sync(p);
              p.connection.teleport(40.5, 32.2, 30.5, 0, 90);
              p.setDeltaMovement(Vec3.ZERO);
            });
      }
      if (ticks > 180 && ticks < 440 && Math.abs(mc.player.getX() + 71.5) < .1) smallCrossed = true;
      if (ticks == 470) {
        check(
            !BottomPassage.visible(mc.level, mc.player.getEyeY()),
            "Disabled fallthrough disables bottom view and selection");
        check(
            PeriodicRenderer.bottomDrawn == 0, "Disabled passage does not draw the opposite exit");
        server(
            p -> {
              p.server.saveEverything(false, true, true);
            });
      }
      if (ticks == 490)
        server(
            p ->
                p.server
                    .getCommands()
                    .performPrefixedCommand(
                        p.createCommandSourceStack().withPermission(4), "planet natural"));
      if (ticks == 550) renderDeadline = System.nanoTime() + 30_000_000_000L;
      if (ticks == 550)
        server(
            p -> {
              var level = p.serverLevel();
              var d = Planet.of(level);
              int b = d.bottom();
              var s = PlanetSettings.get(level);
              s.fallthrough = true;
              s.setDirty();
              PlanetNetwork.sync(p);
              for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                  // Keep the lower four bedrock layers: these used to be an invisible collision
                  // floor.
                  for (int y = b; y <= b + 10; y++)
                    level.setBlock(
                        new BlockPos(40 + dx, y, 30 + dz), Blocks.AIR.defaultBlockState(), 3);
                  for (int y = b; y <= b + 5; y++)
                    level.setBlock(
                        new BlockPos(-776 + dx, y, 30 + dz),
                        (y == b ? Blocks.BEDROCK : Blocks.GLOWSTONE).defaultBlockState(),
                        3);
                }
              p.connection.teleport(40.5, b + .2, 30.5, 0, 90);
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.setDeltaMovement(Vec3.ZERO);
              PlanetServer.reset(p);
            });
      if (ticks == 710) {
        if (PeriodicRenderer.bottomDrawn == 0 && System.nanoTime() < renderDeadline) {
          ticks--;
          return;
        }
        var hit = PlanetClient.pickScene(mc.player, mc.player.blockInteractionRange(), 0, 1);
        check(
            hit instanceof BlockHitResult h
                && h.getType() == HitResult.Type.BLOCK
                && h.getBlockPos().equals(new BlockPos(-776, -496, 30)),
            "Natural opposite bedrock selected across bottom");
        check(PeriodicRenderer.bottomDrawn > 0, "Natural opposite bottom face rendered");
        net.minecraft.client.Screenshot.grab(
            mc.gameDirectory,
            "bottom-natural-bedrock.png",
            mc.getMainRenderTarget(),
            message -> {});
      }
      if (ticks >= 720 && ticks < 1050) {
        var hit = PlanetClient.pickScene(mc.player, mc.player.blockInteractionRange(), 0, 1);
        if (hit instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK)
          mc.gameMode.continueDestroyBlock(h.getBlockPos(), h.getDirection());
      }
      if (ticks == 1060) {
        mc.gameMode.stopDestroyBlock();
        server(
            p -> {
              var d = Planet.of(p.level());
              for (int y = d.bottom(); y <= d.bottom() + 2; y++)
                check(
                    p.level().getBlockState(new BlockPos(-776, y, 30)).isAir(),
                    "Natural survival mining removed opposite block Y=" + y);
              check(
                  p.level().getBlockState(new BlockPos(40, -497, 30)).is(Blocks.BEDROCK),
                  "Lower bedrock storage retained without an invisible collision floor");
              check(
                  Blocks.BEDROCK
                          .defaultBlockState()
                          .getDestroySpeed(p.server.overworld(), BlockPos.ZERO)
                      < 0,
                  "Standard-world bedrock remains unbreakable");
              var ignored =
                  p.level().getBlockCollisions(p, new AABB(40.2, -498, 30.2, 40.8, -496, 30.8));
              check(
                  !ignored.iterator().hasNext(),
                  "Unused lower bedrock has no collision after exit is clear");
              p.getAbilities().flying = false;
              p.onUpdateAbilities();
              p.fallDistance = 0;
              p.setDeltaMovement(0, -.1, 0);
            });
      }
      if (ticks > 1060 && ticks <= 1100 && Math.abs(mc.player.getX() + 775.5) < .1)
        naturalCrossed = true;
      if (ticks == 1100) {
        check(
            naturalCrossed,
            "Natural player physically falls through despite unmined lower bedrock layers");
        server(
            p -> {
              check(p.level().noCollision(p), "Natural exit is clear of embedded collision");
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.setDeltaMovement(Vec3.ZERO);
              p.server.saveEverything(false, true, true);
            });
      }
      if (ticks == 1120) {
        server(
            p -> {
              var level = p.serverLevel();
              var planet = Planet.of(level);
              int b = planet.bottom();
              var settings = PlanetSettings.get(level);
              settings.fallthrough = true;
              settings.setDirty();
              PlanetNetwork.sync(p);
              for (int cx : new int[] {40, -776})
                for (int y = b; y <= b + 96; y++)
                  for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                      level.setBlock(
                          new BlockPos(cx + dx, y, 30 + dz),
                          (Math.abs(dx) == 2 || Math.abs(dz) == 2)
                              ? (y % 8 == 0 ? Blocks.GLOWSTONE : Blocks.GLASS).defaultBlockState()
                              : Blocks.AIR.defaultBlockState(),
                          3);
              p.connection.teleport(40.5, b + 48, 30.5, 0, 90);
              p.getAbilities().mayfly = true;
              p.getAbilities().flying = true;
              p.onUpdateAbilities();
              p.setDeltaMovement(Vec3.ZERO);
              p.connection.send(
                  new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
              PlanetServer.reset(p);
            });
      }
      if (ticks == 1250) {
        check(
            !BottomPassage.visible(mc.level, mc.player.getEyeY()),
            "Far viewer is outside the old 16-block interaction window (Y="
                + mc.player.getY()
                + ", flying="
                + mc.player.getAbilities().flying
                + ")");
        check(
            BottomViewClient.active() && PeriodicRenderer.bottomDrawn > 0,
            "Render-distance view actually draws the exit 48 blocks above bottom");
        net.minecraft.client.Screenshot.grab(
            mc.gameDirectory, "bottom-distance-open.png", mc.getMainRenderTarget(), m -> {});
        server(
            p -> {
              check(
                  PlanetServer.bottomRange(p) > 1,
                  "Server grants extended streaming only for visible shaft");
              p.connection.teleport(40.5, Planet.of(p.level()).bottom() + 48, 30.5, 0, -90);
              p.setDeltaMovement(Vec3.ZERO);
            });
      }
      if (ticks == 1320) {
        check(
            !BottomViewClient.active() && PeriodicRenderer.bottomDrawn == 0,
            "Looking up stops opposite terrain draw");
        check(
            mc.level.getChunkSource().getChunkNow(-776 >> 4, 30 >> 4) == null,
            "Looking away unloads the remote source chunk");
        server(
            p -> {
              check(
                  PlanetServer.bottomRange(p) == -1,
                  "Looking away releases extended streaming lease");
              int b = Planet.of(p.level()).bottom();
              for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                  p.level()
                      .setBlock(
                          new BlockPos(40 + dx, b + 44, 30 + dz),
                          Blocks.STONE.defaultBlockState(),
                          3);
              p.connection.teleport(40.5, b + 48, 30.5, 0, 90);
              p.setDeltaMovement(Vec3.ZERO);
            });
      }
      if (ticks == 1400) {
        check(
            !BottomViewClient.active(),
            "Solid cap occludes the shaft and prevents visual activation");
        server(
            p -> {
              check(
                  PlanetServer.bottomRange(p) == -1,
                  "Occluded shaft does not retain opposite streaming");
              int b = Planet.of(p.level()).bottom();
              for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                  p.level()
                      .setBlock(
                          new BlockPos(40 + dx, b + 44, 30 + dz),
                          Blocks.AIR.defaultBlockState(),
                          3);
            });
        mc.options.renderDistance().set(2);
        mc.options.broadcastOptions();
      }
      if (ticks == 1450) {
        check(
            !BottomViewClient.active(),
            "Two-chunk view budget rejects the 48-block-deep connection");
        mc.options.renderDistance().set(6);
        mc.options.broadcastOptions();
      }
      if (ticks == 1550) {
        check(
            BottomViewClient.active() && PeriodicRenderer.bottomDrawn > 0,
            "Increasing render distance restores the opposite view");
        server(p -> p.server.saveEverything(false, true, true));
      }
      if (ticks == 1580) {
        check(
            net.neoforged.neoforge.client.ClientCommandHandler.runCommand("planet shader false")
                && !PlanetProjection.enabled,
            "Local shader command disables only world projection");
        var flat =
            PlanetClient.project(
                mc.player.getEyePosition().add(3, 2, 1), mc.player.getEyePosition());
        check(
            flat.distanceTo(new Vec3(3, 2, 1)) < 1e-9,
            "Flat diagnostic projection keeps vertex coordinates and selection consistent");
      }
      if (ticks == 1610) {
        check(
            PlanetShaders.shader(0).getUniform("ProjectionEnabled").getFloatBuffer().get(0) == 0,
            "Packaged GPU terrain shader is actually bypassing curvature");
        check(
            BottomViewClient.active() && PeriodicRenderer.bottomDrawn > 0,
            "Bottom view remains available with the curvature shader disabled");
        net.minecraft.client.Screenshot.grab(
            mc.gameDirectory, "bottom-flat-shader-off.png", mc.getMainRenderTarget(), m -> {});
        server(
            p ->
                check(
                    PlanetSettings.get(p.level()).fallthrough,
                    "Shader toggle does not disable bottom physics"));
        net.neoforged.neoforge.client.ClientCommandHandler.runCommand("planet shader true");
      }
      if (ticks == 1640) {
        check(
            PlanetProjection.enabled
                && PlanetShaders.shader(0).getUniform("ProjectionEnabled").getFloatBuffer().get(0)
                    == 1,
            "Shader command restores the curved world");
      }
      if (ticks == 1660) {
        Files.write(mc.gameDirectory.toPath().resolve("bottom-client-results.txt"), results);
        SpaceBlocks.LOGGER.info("BOTTOM_CLIENT_TEST_PASS");
        done = true;
        mc.stop();
      }
      if (ticks >= 1130 && ticks < 1660) mc.player.setDeltaMovement(Vec3.ZERO);
    } catch (Throwable failure) {
      SpaceBlocks.LOGGER.error("BOTTOM_CLIENT_TEST_FAIL", failure);
      try {
        Files.writeString(
            mc.gameDirectory.toPath().resolve("bottom-client-results.txt"), "FAIL " + failure);
      } catch (Exception ignored) {
      }
      done = true;
      mc.stop();
    }
  }
}
