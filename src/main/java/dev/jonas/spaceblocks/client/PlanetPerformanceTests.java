package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Cold natural-world streaming test at the user's 12-chunk view distance. Only opt-in isolated runs. */
public final class PlanetPerformanceTests {
  private static int ticks, stable;
  private static long start;
  private static boolean finished;
  private static double firstSurface = -1;
  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null || finished) return;
    ticks++;
    if (ticks == 1) {
      mc.options.pauseOnLostFocus = false;
      mc.options.renderDistance().set(12);
      mc.options.simulationDistance().set(12);
      mc.options.framerateLimit().set(120);
      mc.options.broadcastOptions();
      start = System.nanoTime();
      var server = mc.getSingleplayerServer();
      var id = mc.player.getUUID();
      server.execute(() -> {
        var player = server.getPlayerList().getPlayer(id);
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "planet natural");
      });
    }
    if (!mc.level.dimension().equals(SpaceBlocks.NATURAL)) return;
    int chunks = 0, ready = 0, expected = 0;
    int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
    for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
      // Require all chunks that the renderer actually admits, including its one-section margin.
      double distance = Math.hypot((cx + dx)*16 - mc.player.getX(), (cz + dz)*16 - mc.player.getZ());
      if (distance > 208) continue;
      expected++;
      var chunk = mc.level.getChunkSource().getChunk(cx+dx, cz+dz, ChunkStatus.FULL, false);
      if (chunk == null || chunk instanceof net.minecraft.world.level.chunk.EmptyLevelChunk) continue;
      chunks++;
      int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
      if (PeriodicRenderer.ready(new BlockPos((cx+dx)*16, Math.floorDiv(y,16)*16, (cz+dz)*16))) ready++;
    }
    double seconds = (System.nanoTime()-start)/1e9;
    if (ready > 0 && firstSurface < 0) firstSurface = seconds;
    if (ticks % 100 == 0) SpaceBlocks.LOGGER.info("PERIODIC_PERFORMANCE seconds={} chunks={}/{} surface={}/{} cached={} pending={}", seconds, chunks, expected, ready, expected, PeriodicRenderer.cachedSections(), PeriodicRenderer.pendingSections);
    if (chunks == expected && ready == expected && PeriodicRenderer.pendingSections == 0) stable++; else stable = 0;
    boolean pass = stable >= 40;
    if (pass || seconds > 240) {
      finished = true;
      var result = (pass ? "PASS" : "FAIL") + " cold natural view=12 simulation=12 seconds="+seconds+" firstSurface="+firstSurface+" chunks="+chunks+"/"+expected+" surface="+ready+"/"+expected+" cached="+PeriodicRenderer.cachedSections()+" pending="+PeriodicRenderer.pendingSections;
      SpaceBlocks.LOGGER.info("PERIODIC_PERFORMANCE_TEST_{} {}", pass ? "PASS" : "FAIL", result);
      try { Files.writeString(mc.gameDirectory.toPath().resolve("periodic-performance-results.txt"), result); }
      catch (java.io.IOException ex) { throw new RuntimeException(ex); }
      net.minecraft.client.Screenshot.grab(mc.gameDirectory, "periodic-performance-12.png", mc.getMainRenderTarget(), message -> {});
      mc.stop();
    }
  }
}
