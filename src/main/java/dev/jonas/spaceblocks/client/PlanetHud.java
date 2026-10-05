package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Small, schematic whole-map atlas, never a second world renderer or chunk loader. */
public final class PlanetHud {
  private static PlanetAtlas.Snapshot data;
  private static String dimension = "";
  private static long nextRequest, crossing, lastFrame;
  private static double yaw, pitch;
  private static float crossingPitch;
  private static int crossings;
  public static int renderedTriangles;
  public static boolean renderedMarker;

  public static int crossingCount() {
    return crossings;
  }

  public static boolean hasAtlas() {
    return data != null;
  }

  public static void receive(PlanetAtlas.Snapshot snapshot) {
    var mc = Minecraft.getInstance();
    if (mc.level != null && mc.level.dimension().location().toString().equals(snapshot.dimension()))
      data = snapshot;
  }

  public static void crossed() {
    crossed(0, 0);
  }

  public static void crossed(float before, float after) {
    crossingPitch = before - after;
    crossing = System.nanoTime();
    crossings++;
  }

  public static void camera(ViewportEvent.ComputeCameraAngles e) {
    PlanetHudConfig.load();
    if (!PlanetClient.active()) {
      crossing = 0;
      return;
    }
    if (!PlanetHudConfig.transition || crossing == 0) return;
    double t = (System.nanoTime() - crossing) / (PlanetHudConfig.duration * 1_000_000.0);
    double clamped = Math.max(0, Math.min(1, t));
    double eased = clamped * clamped * (3 - 2 * clamped);
    e.setPitch(e.getPitch() + (float) (crossingPitch * (1 - eased)));
    e.setRoll(e.getRoll() + HudGeometry.roll(t));
    if (t >= 1) crossing = 0;
  }

  public static void commands(RegisterClientCommandsEvent e) {
    e.getDispatcher()
        .register(
            Commands.literal("planet")
                .then(
                    Commands.literal("shader")
                        .executes(
                            c -> {
                              Minecraft.getInstance()
                                  .player
                                  .sendSystemMessage(
                                      net.minecraft.network.chat.Component.literal(
                                          "Planet projection shader=" + PlanetProjection.enabled));
                              return 1;
                            })
                        .then(
                            Commands.argument(
                                    "enabled",
                                    com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(
                                    c -> {
                                      PlanetProjection.enabled =
                                          com.mojang.brigadier.arguments.BoolArgumentType.getBool(
                                              c, "enabled");
                                      BottomViewClient.refresh();
                                      Minecraft.getInstance()
                                          .player
                                          .sendSystemMessage(
                                              net.minecraft.network.chat.Component.literal(
                                                  "Planet projection shader="
                                                      + PlanetProjection.enabled
                                                      + ". Periodic world and physics unchanged."));
                                      return 1;
                                    })))
                .then(
                    Commands.literal("hud")
                        .executes(
                            c -> {
                              var mc = Minecraft.getInstance();
                              mc.execute(() -> mc.setScreen(new PlanetHudScreen(null)));
                              return 1;
                            })));
  }

  private static AtlasGeometry.Point point(double u, double v) {
    return AtlasGeometry.rotate(AtlasGeometry.globe(u, 1 - v, 1), yaw, pitch);
  }

  public static void render(RenderGuiEvent.Post event) {
    PlanetHudConfig.load();
    var mc = Minecraft.getInstance();
    var planet = PlanetClient.planet();
    if (planet == null || mc.player == null) {
      data = null;
      dimension = "";
      crossing = 0;
      crossings = 0;
      return;
    }
    if (!PlanetHudConfig.enabled || mc.options.hideGui) return;
    long now = System.nanoTime();
    String current = mc.level.dimension().location().toString();
    double u = (mc.player.getX() + planet.size() / 2.0) / planet.size();
    double v = (mc.player.getZ() + planet.size() / 2.0) / planet.size();
    double targetYaw = -u * Math.PI * 2, targetPitch = (.5 - v) * Math.PI;
    if (!current.equals(dimension)) {
      dimension = current;
      data = null;
      nextRequest = 0;
      crossing = 0;
      crossings = 0;
      yaw = targetYaw;
      pitch = targetPitch;
      lastFrame = now;
    }
    double alpha = 1 - Math.exp(-Math.min(.1, (now - lastFrame) / 1_000_000_000.0) * 5);
    lastFrame = now;
    yaw += HudGeometry.angleDelta(targetYaw, yaw) * alpha;
    pitch += (targetPitch - pitch) * alpha;
    if (now >= nextRequest && mc.screen == null) {
      PacketDistributor.sendToServer(new PlanetAtlas.Request(true));
      nextRequest = now + 30_000_000_000L;
    }
    var g = event.getGuiGraphics();
    int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
    int size = Math.min(PlanetHudConfig.size, Math.max(48, Math.min(width - 16, height - 64)));
    int panelHeight = size + 45;
    boolean right = PlanetHudConfig.corner % 2 == 0, bottom = PlanetHudConfig.corner >= 2;
    int x =
        Math.clamp(
            right ? width - size - PlanetHudConfig.marginX : PlanetHudConfig.marginX,
            0,
            Math.max(0, width - size));
    int y =
        Math.clamp(
            bottom ? height - panelHeight - PlanetHudConfig.marginY : PlanetHudConfig.marginY,
            0,
            Math.max(0, height - panelHeight));
    draw(g, x, y, size);
    g.drawCenteredString(
        mc.font,
        HudGeometry.heading(mc.player.getYRot()) + "  |  N:-Z E:+X",
        x + size / 2,
        y + size + 2,
        0xFFFFFF);
    g.drawCenteredString(
        mc.font,
        String.format(java.util.Locale.ROOT, "X %.0f Z %.0f", mc.player.getX(), mc.player.getZ()),
        x + size / 2,
        y + size + 13,
        0xB4D8EF);
    g.drawCenteredString(
        mc.font,
        String.format(
            java.util.Locale.ROOT, "Y %.0f  R %.2f", mc.player.getY(), planet.projectionRadius()),
        x + size / 2,
        y + size + 24,
        0xB4D8EF);
    if (crossing != 0)
      g.drawCenteredString(
          mc.font, "Bottom crossing #" + crossings, x + size / 2, y + size + 35, 0xFFD879);
  }

  private static void draw(GuiGraphics g, int x, int y, int size) {
    g.fill(x, y, x + size, y + size + 45, 0xB5101B2B);
    g.drawCenteredString(
        Minecraft.getInstance().font, "Globe atlas", x + size / 2, y + 2, 0x9FC8DD);
    float cx = x + size / 2f, cy = y + size / 2f + 5, radius = (size - 22) / 2f;
    g.flush();
    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    RenderSystem.disableDepthTest();
    RenderSystem.disableCull();
    RenderSystem.enableBlend();
    renderedTriangles = 0;
    try {
      var buffer =
          Tesselator.getInstance()
              .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      int n = 24, m = 12;
      for (int row = 0; row < m; row++)
        for (int col = 0; col < n; col++) {
          double u = col / (double) n, v = row / (double) m;
          var a = point(u, v);
          var b = point(u + 1.0 / n, v);
          var c = point(u + 1.0 / n, v + 1.0 / m);
          var d = point(u, v + 1.0 / m);
          int color = 0x528B65;
          if (data != null) {
            int resolution = data.resolution();
            int sx = Math.min(resolution - 1, (int) ((u + .5 / n) * resolution));
            int sz = Math.min(resolution - 1, (int) ((v + .5 / m) * resolution));
            color = data.colors()[sz * resolution + sx];
          }
          for (var tri : new AtlasGeometry.Point[][] {{a, b, c}, {a, c, d}}) {
            double front = (tri[0].z() + tri[1].z() + tri[2].z()) / 3;
            if (front <= 0) continue;
            double shade = .35 + .65 * front;
            int shaded =
                0xFF000000
                    | (int) (((color >> 16) & 255) * shade) << 16
                    | (int) (((color >> 8) & 255) * shade) << 8
                    | (int) ((color & 255) * shade);
            for (var p : tri)
              buffer
                  .addVertex(
                      g.pose().last().pose(),
                      cx + (float) p.x() * radius,
                      cy - (float) p.y() * radius,
                      0)
                  .setColor(shaded);
            renderedTriangles++;
          }
        }
      var mesh = buffer.build();
      if (mesh != null) BufferUploader.drawWithShader(mesh);
    } finally {
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
    }
    var mc = Minecraft.getInstance();
    var planet = PlanetClient.planet();
    var p =
        point(
            (mc.player.getX() + planet.size() / 2.0) / planet.size(),
            (mc.player.getZ() + planet.size() / 2.0) / planet.size());
    int mx = (int) (cx + p.x() * radius), my = (int) (cy - p.y() * radius);
    renderedMarker = p.z() > 0;
    if (renderedMarker) {
      g.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFFFFD879);
      double heading = Math.toRadians(mc.player.getYRot());
      int tx = mx - (int) (Math.sin(heading) * 7), ty = my + (int) (Math.cos(heading) * 7);
      g.fill(tx - 1, ty - 1, tx + 2, ty + 2, 0xFFFFFFFF);
    } else g.drawCenteredString(mc.font, "Far side", x + size / 2, y + size - 10, 0xFFD879);
  }
}
