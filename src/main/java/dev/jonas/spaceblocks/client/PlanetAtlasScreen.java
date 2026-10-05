package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlanetAtlasScreen extends Screen {
  private record Face(
      AtlasGeometry.Point a,
      AtlasGeometry.Point b,
      AtlasGeometry.Point c,
      int color,
      boolean confirmed) {}

  private record DrawFace(
      AtlasGeometry.Point a,
      AtlasGeometry.Point b,
      AtlasGeometry.Point c,
      int color,
      double depth) {}

  private PlanetAtlas.Snapshot data;
  private final List<Face> faces = new ArrayList<>();
  private final List<DrawFace> ordered = new ArrayList<>();
  private double yaw = .45, pitch = -.22, zoom = 1;
  private boolean exact, coverage, waiting;
  private double lastYaw = Double.NaN, lastPitch;
  public int renderedTriangles;
  private VertexBuffer preview;
  private double builtZoom = Double.NaN;
  private int builtWidth, builtHeight;

  public PlanetAtlas.Snapshot snapshot() {
    return data;
  }

  public double yawValue() {
    return yaw;
  }

  private int left, top, right, bottom;
  private Button projectionButton, coverageButton, refreshButton;
  private long nextRefresh, refreshDeadline;

  public PlanetAtlasScreen(PlanetAtlas.Snapshot data) {
    super(Component.literal("Planet Atlas"));
    this.data = data;
    nextRefresh = System.nanoTime() + 2_000_000_000L;
    rebuild();
  }

  public static void receive(PlanetAtlas.Snapshot data) {
    PlanetHud.receive(data);
    var mc = Minecraft.getInstance();
    if (mc.level == null || !mc.level.dimension().location().toString().equals(data.dimension()))
      return;
    if (mc.screen instanceof PlanetAtlasScreen screen) {
      screen.data = data;
      screen.waiting = false;
      screen.nextRefresh = System.nanoTime() + 2_000_000_000L;
      screen.rebuild();
    } else mc.setScreen(new PlanetAtlasScreen(data));
  }

  @Override
  protected void init() {
    left = 12;
    top = 44;
    right = Math.max(left + 80, width - 202);
    bottom = height - 30;
    int sidebar = right + 12;
    projectionButton =
        addRenderableWidget(
            Button.builder(
                    Component.literal(exact ? "Spheretest view" : "Globe atlas"),
                    b -> {
                      exact = !exact;
                      b.setMessage(Component.literal(exact ? "Spheretest view" : "Globe atlas"));
                      rebuild();
                    })
                .bounds(sidebar, top + 106, 176, 20)
                .build());
    coverageButton =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Loaded: " + (coverage ? "ON" : "OFF")),
                    b -> {
                      coverage = !coverage;
                      b.setMessage(Component.literal("Loaded: " + (coverage ? "ON" : "OFF")));
                      lastYaw = Double.NaN;
                    })
                .bounds(sidebar, top + 130, 84, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.literal("Center"), b -> centerPlayer())
            .bounds(sidebar + 92, top + 130, 84, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal("Reset"),
                b -> {
                  yaw = .45;
                  pitch = -.22;
                  zoom = 1;
                })
            .bounds(sidebar, top + 154, 84, 20)
            .build());
    refreshButton =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Refresh"),
                    b -> {
                      waiting = true;
                      refreshDeadline = System.nanoTime() + 15_000_000_000L;
                      PacketDistributor.sendToServer(new PlanetAtlas.Request());
                    })
                .bounds(sidebar + 92, top + 154, 84, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(width - 82, 10, 70, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal("Planets"),
                b -> PacketDistributor.sendToServer(PlanetManagerNetwork.Action.list()))
            .bounds(width - 162, 10, 70, 20)
            .build());
  }

  public void centerPlayer() {
    var p = playerPoint();
    yaw = -Math.atan2(p.x(), p.z());
    pitch = Math.atan2(-p.y(), Math.hypot(p.x(), p.z()));
    zoom = 1;
  }

  public double zoomValue() {
    return zoom;
  }

  public boolean exactMode() {
    return exact;
  }

  public void toggleProjection() {
    exact = !exact;
    projectionButton.setMessage(Component.literal(exact ? "Spheretest view" : "Globe atlas"));
    rebuild();
  }

  // Compress relief only in the schematic atlas, never in the gameplay projection.
  private double atlasRadius(double height) {
    return 1 + .18 * Math.tanh((height - Planet.SURFACE) / data.radius());
  }

  private AtlasGeometry.Point point(int x, int z) {
    int n = data.resolution(), i = Math.floorMod(z, n) * n + Math.floorMod(x, n);
    double h = data.heights()[i], u = x / (double) n, v = z / (double) n;
    if (exact)
      return AtlasGeometry.spheretest(
          -data.size() / 2.0 + u * data.size(),
          -data.size() / 2.0 + v * data.size(),
          h,
          data.playerX(),
          data.playerZ(),
          data.radius(),
          data.size());
    if (z == 0 || z == n) {
      h = 0;
      for (int j = 0; j < n; j++) h += data.heights()[j];
      h /= n;
    }
    return AtlasGeometry.globe(u, v, atlasRadius(h));
  }

  private void rebuild() {
    faces.clear();
    int n = data.resolution();
    for (int z = 0; z < n; z++)
      for (int x = 0; x < n; x++) {
        if (exact) {
          double dx =
              PeriodicMath.wrap(
                  PlanetAtlas.coordinate(x, n, data.size()) - data.playerX(), data.size());
          double dz =
              PeriodicMath.wrap(
                  PlanetAtlas.coordinate(z, n, data.size()) - data.playerZ(), data.size());
          if (Math.hypot(dx, dz) > data.size() / 4.0 - 2 * data.size() / (double) n) continue;
        }
        var a = point(x, z);
        var b = point(x + 1, z);
        var c = point(x + 1, z + 1);
        var d = point(x, z + 1);
        int i = z * n + x, color = data.colors()[i];
        boolean confirmed = data.confirmed()[i];
        if (exact) {
          faces.add(new Face(a, c, b, color, confirmed));
          faces.add(new Face(a, d, c, color, confirmed));
        } else {
          faces.add(new Face(a, b, c, color, confirmed));
          faces.add(new Face(a, c, d, color, confirmed));
        }
      }
    lastYaw = Double.NaN;
  }

  private AtlasGeometry.Point playerPoint() {
    int n = data.resolution(),
        x =
            Math.floorMod(
                (int) Math.floor((data.playerX() + data.size() / 2.0) * n / data.size()), n);
    int z =
        Math.floorMod((int) Math.floor((data.playerZ() + data.size() / 2.0) * n / data.size()), n);
    double height = data.heights()[z * n + x] + 3;
    if (exact)
      return AtlasGeometry.spheretest(
          data.playerX(),
          data.playerZ(),
          height,
          data.playerX(),
          data.playerZ(),
          data.radius(),
          data.size());
    return AtlasGeometry.globe(
        (data.playerX() + data.size() / 2.0) / data.size(),
        (data.playerZ() + data.size() / 2.0) / data.size(),
        atlasRadius(height));
  }

  private static int shade(int color, double light) {
    int r = (int) (((color >> 16) & 255) * light),
        g = (int) (((color >> 8) & 255) * light),
        b = (int) ((color & 255) * light);
    return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
  }

  private void transform() {
    if (yaw == lastYaw && pitch == lastPitch) return;
    ordered.clear();
    for (var face : faces) {
      var a = AtlasGeometry.rotate(face.a, yaw, pitch);
      var b = AtlasGeometry.rotate(face.b, yaw, pitch);
      var c = AtlasGeometry.rotate(face.c, yaw, pitch);
      double ux = b.x() - a.x(),
          uy = b.y() - a.y(),
          uz = b.z() - a.z(),
          vx = c.x() - a.x(),
          vy = c.y() - a.y(),
          vz = c.z() - a.z();
      double nx = uy * vz - uz * vy,
          ny = uz * vx - ux * vz,
          nz = ux * vy - uy * vx,
          length = Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (length < 1e-12) continue;
      double light = .38 + .62 * Math.max(0, (-nx * .35 + ny * .55 + nz * .75) / length);
      int color = coverage ? (face.confirmed ? 0x52C79B : 0x576B88) : face.color;
      ordered.add(new DrawFace(a, b, c, shade(color, light), (a.z() + b.z() + c.z()) / 3));
    }
    ordered.sort(Comparator.comparingDouble(DrawFace::depth));
    lastYaw = yaw;
    lastPitch = pitch;
  }

  private double scale() {
    return Math.max(20, Math.min(right - left, bottom - top) * .36) * zoom;
  }

  private double screenX(AtlasGeometry.Point p) {
    return (left + right) / 2.0 + p.x() * scale() * 4.5 / (4.5 - p.z());
  }

  private double screenY(AtlasGeometry.Point p) {
    return (top + bottom) / 2.0 - p.y() * scale() * 4.5 / (4.5 - p.z());
  }

  @Override
  public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

  @Override
  public void tick() {
    if (minecraft.level == null
        || !minecraft.level.dimension().location().toString().equals(data.dimension())) {
      onClose();
      return;
    }
    if (waiting && System.nanoTime() >= refreshDeadline) waiting = false;
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    g.fill(0, 0, width, height, 0xF20B101C);
    g.fill(left, top, right, bottom, 0xFF111B2B);
    g.drawString(font, "PLANET ATLAS", 16, 15, 0xEAF2FF, false);
    g.drawString(
        font,
        exact
            ? "Spheretest camera projection · near hemisphere"
            : "Whole-map globe · schematic atlas",
        16,
        29,
        0x96B4D3,
        false);
    boolean changed =
        lastYaw != yaw
            || lastPitch != pitch
            || builtZoom != zoom
            || builtWidth != width
            || builtHeight != height;
    transform();
    g.flush();
    g.enableScissor(left, top, right, bottom);
    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    RenderSystem.disableDepthTest();
    RenderSystem.disableCull();
    RenderSystem.enableBlend();
    try {
      if (changed || preview == null) {
        try (var memory = new ByteBufferBuilder(Math.max(4096, ordered.size() * 3 * 16))) {
          var buffer =
              new BufferBuilder(
                  memory, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
          for (var face : ordered)
            for (var p : new AtlasGeometry.Point[] {face.a, face.b, face.c})
              buffer
                  .addVertex(g.pose().last().pose(), (float) screenX(p), (float) screenY(p), 0)
                  .setColor(face.color);
          var mesh = buffer.build();
          if (preview != null) preview.close();
          preview = null;
          if (mesh != null) {
            preview = new VertexBuffer(VertexBuffer.Usage.STATIC);
            preview.bind();
            preview.upload(mesh);
            VertexBuffer.unbind();
          }
        }
        builtZoom = zoom;
        builtWidth = width;
        builtHeight = height;
      }
      if (preview != null) {
        preview.bind();
        preview.drawWithShader(
            RenderSystem.getModelViewMatrix(),
            RenderSystem.getProjectionMatrix(),
            GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
      }
    } finally {
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      g.disableScissor();
    }
    renderedTriangles = ordered.size();
    var marker = AtlasGeometry.rotate(playerPoint(), yaw, pitch);
    if (marker.z() > 0) {
      int mx = (int) screenX(marker), my = (int) screenY(marker);
      if (mx >= left && mx < right && my >= top && my < bottom) {
        g.fill(mx - 3, my - 3, mx + 4, my + 4, 0xFFFFD879);
        g.drawString(font, "You", mx + 7, my - 3, 0xFFF2BC, false);
      }
    }
    int x = right + 12, y = top;
    g.drawString(font, "PLANET INFORMATION", x, y, 0xD8E6F8, false);
    g.drawString(font, data.dimension().replace("spaceblocks:", ""), x, y + 16, 0x91AED0, false);
    g.drawString(
        font, "Map: " + data.size() + " x " + data.size() + " blocks", x, y + 36, 0xFFFFFF, false);
    g.drawString(
        font,
        String.format(
            java.util.Locale.ROOT,
            "Radius: %.2f (requested %d)",
            data.size() / (2.0 * Math.PI),
            data.radius()),
        x,
        y + 50,
        0xFFFFFF,
        false);
    g.drawString(font, "Bottom: Y=" + data.bottom(), x, y + 64, 0xFFFFFF, false);
    g.drawString(
        font,
        String.format(java.util.Locale.ROOT, "Player: %.0f, %.0f", data.playerX(), data.playerZ()),
        x,
        y + 78,
        0xFFFFFF,
        false);
    g.drawString(
        font,
        "Loaded samples: " + (100 * data.confirmedCount() / data.heights().length) + "%",
        x,
        y + 92,
        0x9CE1BC,
        false);
    g.drawString(font, "Drag to rotate · Scroll to zoom", left, height - 27, 0xA8BFD9, false);
    g.drawString(
        font,
        waiting ? "Refreshing..." : "Unloaded areas use a terrain forecast",
        left,
        height - 15,
        0x738EAB,
        false);
    refreshButton.active = !waiting && System.nanoTime() >= nextRefresh;
    super.render(g, mouseX, mouseY, partialTick);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (button == 0 && x >= left && x < right && y >= top && y < bottom) {
      yaw += dx * .012;
      pitch = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, pitch + dy * .012));
      return true;
    }
    return super.mouseDragged(x, y, button, dx, dy);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (x >= left && x < right && y >= top && y < bottom) {
      zoom = Math.max(.45, Math.min(2.8, zoom * Math.pow(1.12, vertical)));
      return true;
    }
    return super.mouseScrolled(x, y, horizontal, vertical);
  }

  @Override
  public void removed() {
    if (preview != null) {
      preview.close();
      preview = null;
    }
    super.removed();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
