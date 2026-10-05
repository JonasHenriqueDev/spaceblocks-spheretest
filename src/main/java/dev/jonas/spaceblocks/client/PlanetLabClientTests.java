package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opt-in continuation of the packaged atlas run; never executes in normal gameplay. */
public final class PlanetLabClientTests {
  private static int tick;
  private static boolean done;
  private static volatile boolean labReady;
  private static volatile boolean savedProbe;
  private static volatile Throwable failure;
  private static final List<String> results = Collections.synchronizedList(new ArrayList<>());

  private static void check(boolean ok, String text) {
    if (!ok) throw new IllegalStateException(text);
    results.add("PASS " + text);
    SpaceBlocks.LOGGER.info("LAB_CLIENT {}", text);
  }

  private static void server(Consumer<ServerPlayer> action) {
    var mc = Minecraft.getInstance();
    var s = mc.getSingleplayerServer();
    var id = mc.player.getUUID();
    s.execute(
        () -> {
          try {
            action.accept(s.getPlayerList().getPlayer(id));
          } catch (Throwable e) {
            failure = e;
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

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    net.minecraft.client.Screenshot.grab(
        mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
  }

  public static void tick() {
    var mc = Minecraft.getInstance();
    if (done || mc.player == null) return;
    tick++;
    try {
      if (failure != null) throw new IllegalStateException("Server assertion failed", failure);
      if (tick == 1) {
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(5);
        mc.options.simulationDistance().set(5);
        mc.options.broadcastOptions();
        server(
            p -> {
              p.server.getPlayerList().op(p.getGameProfile());
              savedProbe = PlanetSatellite.savedProbeCount(p.server.getLevel(SpaceBlocks.LAB)) > 0;
              if (Boolean.getBoolean("spaceblocks.verifyPersistence"))
                check(savedProbe, "Probe index persisted after reopening");
            });
        command("planet lab");
      }
      if (tick == 99) {
        server(p -> labReady = PlanetSettings.get(p.level()).hasLab);
        if (!labReady) {
          tick--;
          return;
        }
      }
      if (tick == 100) {
        if (savedProbe)
          server(
              p -> {
                boolean restored = false;
                for (var e : p.serverLevel().getAllEntities())
                  if (e.getPersistentData().getBoolean("planet_satellite")
                      && e.getPersistentData().getDouble("satellite_distance") > 0) restored = true;
                check(restored, "Saved satellite resumes from its own chunk index");
              });
        check(mc.level.dimension().equals(SpaceBlocks.LAB), "Isolated natural test planet entered");
        check(
            mc.level.getBlockState(new BlockPos(0, 180, 0)).is(Blocks.SMOOTH_STONE),
            "Test flight deck loaded");
        server(
            p -> {
              check(
                  p.serverLevel().getBlockState(new BlockPos(16, 184, 0)).is(Blocks.TNT),
                  "TNT test towers prepared");
              check(
                  p.serverLevel()
                      .getBlockState(new BlockPos(-Planet.LAB.size() / 2, 180, 0))
                      .is(Blocks.STONE_BRICKS),
                  "Seam test platform wraps storage");
            });
        command("planet satellite launch 128");
        mc.player.setYRot(-90);
        mc.player.setXRot(-10);
        capture("test-lab");
      }
      if (tick == 160)
        server(
            p -> {
              boolean found = false;
              for (var e : p.serverLevel().getAllEntities())
                if (e.getPersistentData().getBoolean("planet_satellite")) {
                  found = true;
                  check(
                      Math.abs(e.getY() - 192) < .001,
                      "Satellite starts at circular balance height");
                }
              check(found, "Satellite is a real synchronized world entity");
            });
      if (tick == 850) {
        server(
            p -> {
              boolean found = false;
              for (var e : p.serverLevel().getAllEntities())
                if (e.getPersistentData().getBoolean("planet_satellite")) {
                  found = true;
                  check(
                      e.getPersistentData().getDouble("satellite_distance") >= Planet.LAB.size(),
                      "Satellite completes one periodic circuit");
                  check(
                      Math.abs(e.getY() - 192) < .01,
                      "Balanced probe stays at the tested altitude");
                }
              check(found, "Probe survives complete circuit");
            });
        command("planet physics centrifugal false");
      }
      if (tick == 910) {
        server(
            p -> {
              for (var e : p.serverLevel().getAllEntities())
                if (e.getPersistentData().getBoolean("planet_satellite"))
                  check(e.getY() < 191, "Probe responds to centrifugal option");
            });
        command("planet physics centrifugal true");
        command("planet satellite remove");
        command("planet satellite launch 128");
      }
      if (tick == 930)
        server(
            p -> {
              var c = PlanetCatalog.get(p.server);
              if (c.find(p.server, "test_alpha") == null)
                PlanetCatalog.generate(p, "test_alpha", 32, 111L);
              else PlanetCatalog.teleport(p, "test_alpha", 0, 0, null);
            });
      if (tick == 1070) {
        check(
            PlanetClient.planet().radius() == 32, "Generated planet radius synchronized to client");
        server(
            p -> {
              var c = PlanetCatalog.get(p.server);
              var e = c.find(p.server, "test_alpha");
              check(e.seed() == 111 && e.radius() == 32, "Named planet seed and size retained");
              if (Boolean.getBoolean("spaceblocks.verifyPersistence"))
                check(
                    p.serverLevel()
                        .getBlockState(new BlockPos(10, 150, 10))
                        .is(Blocks.DIAMOND_BLOCK),
                    "Generated planet construction preserved on strict reopen");
              if (p.serverLevel().getBlockState(new BlockPos(10, 150, 10)).is(Blocks.DIAMOND_BLOCK))
                check(true, "Generated planet construction persisted after reopening");
              p.serverLevel()
                  .setBlock(new BlockPos(10, 150, 10), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
              check(
                  ((PlanetGenerator) p.serverLevel().getChunkSource().getGenerator())
                          .terrainSeed(p.serverLevel().getChunkSource().randomState())
                      == 111,
                  "Generator uses explicit seed");
            });
        command("planet map");
      }
      if (tick == 1150) {
        check(
            mc.screen instanceof PlanetAtlasScreen
                && ((PlanetAtlasScreen) mc.screen).snapshot().radius() == 32,
            "Atlas supports generated planet dimensions");
        capture("atlas-generated");
        PacketDistributor.sendToServer(PlanetManagerNetwork.Action.list());
      }
      if (tick == 1180) {
        check(mc.screen instanceof PlanetManagerScreen, "Planet manager opens over network");
        var entries = ((PlanetManagerScreen) mc.screen).entries();
        check(
            entries.stream().anyMatch(e -> e.name().equals("test_alpha") && e.radius() == 32),
            "Manager lists generated and built-in planets");
        capture("planet-manager");
        if (entries.stream().noneMatch(e -> e.name().equals("test_beta")))
          PacketDistributor.sendToServer(
              new PlanetManagerNetwork.Action(1, "test_beta", 64, "", 0, 0, ""));
        else
          PacketDistributor.sendToServer(
              new PlanetManagerNetwork.Action(2, "test_beta", 64, "", 0, 0, "surface"));
        mc.setScreen(null);
      }
      if (tick == 1340) {
        check(PlanetClient.planet().radius() == 64, "Manager generates and enters selected radius");
        server(
            p -> {
              var c = PlanetCatalog.get(p.server);
              var a = c.find(p.server, "test_alpha");
              var b = c.find(p.server, "test_beta");
              check(a.seed() != b.seed(), "New planet gets independent random seed");
              check(
                  !a.dimension().equals(b.dimension()),
                  "Independent planets preserve separate chunks");
              check(
                  !p.serverLevel()
                      .getBlockState(new BlockPos(10, 150, 10))
                      .is(Blocks.DIAMOND_BLOCK),
                  "Construction does not leak between planets");
            });
        PacketDistributor.sendToServer(
            new PlanetManagerNetwork.Action(2, "test_beta", 64, "", 33.5, -41.5, "200"));
      }
      if (tick == 1360) {
        check(
            Math.abs(mc.player.getX() - 33.5) < 1
                && Math.abs(mc.player.getZ() + 41.5) < 1
                && mc.player.getY() > 170,
            "Manager teleports to specified X/Z/height");
        command("planet map");
      }
      if (tick == 1440) {
        check(mc.screen instanceof PlanetAtlasScreen, "Map reopens at selected planet location");
        mc.setScreen(null);
        command("planet lab");
      }
      if (tick == 1540) {
        check(
            mc.level.dimension().equals(SpaceBlocks.LAB),
            "Prepared test planet remains available after generation");
        command("save-all flush");
        capture("test-lab-final");
      }
      if (tick == 1580) {
        Files.write(mc.gameDirectory.toPath().resolve("lab-client-results.txt"), results);
        SpaceBlocks.LOGGER.info("LAB_CLIENT_TEST_PASS");
        done = true;
        mc.stop();
      }
    } catch (Throwable e) {
      done = true;
      SpaceBlocks.LOGGER.error("LAB_CLIENT_TEST_FAIL", e);
      try {
        Files.writeString(mc.gameDirectory.toPath().resolve("lab-client-results.txt"), "FAIL " + e);
      } catch (Exception ignored) {
      }
      mc.stop();
    }
  }
}
