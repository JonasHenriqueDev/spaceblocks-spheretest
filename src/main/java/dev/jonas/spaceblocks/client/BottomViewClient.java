package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BottomViewClient {
  public static void forget(PlanetNetwork.ForgetChunk packet) {
    var level = Minecraft.getInstance().level;
    if (level != null && Planet.of(level) != null)
      ((PeriodicChunkAccess) level.getChunkSource())
          .dropPeriodic(new net.minecraft.world.level.ChunkPos(packet.x(), packet.z()));
  }

  private static long nextQuery, nextSend;
  private static Object level;
  private static boolean visible, sent;

  public static void refresh() {
    nextQuery = 0;
    nextSend = 0;
  }

  public static boolean active() {
    return visible;
  }

  public static void update() {
    var mc = Minecraft.getInstance();
    if (mc.level != level) {
      level = mc.level;
      visible = false;
      sent = false;
      nextQuery = nextSend = 0;
    }
    if (!PlanetClient.active() || mc.player == null) {
      visible = false;
      return;
    }
    long now = System.nanoTime();
    if (now >= nextQuery) {
      var camera = mc.gameRenderer.getMainCamera();
      var look = camera.getLookVector();
      visible =
          BottomView.looking(
              mc.level,
              mc.player,
              camera.getPosition(),
              new Vec3(look.x(), look.y(), look.z()),
              mc.options.getEffectiveRenderDistance(),
              PlanetProjection.enabled);
      nextQuery = now + 100_000_000L;
    }
    if (sent != visible || now >= nextSend) {
      PacketDistributor.sendToServer(
          new PlanetNetwork.BottomViewRequest(visible, PlanetProjection.enabled));
      sent = visible;
      nextSend = now + 500_000_000L;
    }
  }
}
