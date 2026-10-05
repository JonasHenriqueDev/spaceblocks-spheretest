package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlanetManagerScreen extends Screen {
  private List<PlanetCatalog.Entry> entries;
  private int page;
  private String selected = "natural", error = "";
  private EditBox name, radius, seed, x, z, heightField;
  private Button generateButton;

  public PlanetManagerScreen(List<PlanetCatalog.Entry> entries) {
    super(Component.literal("Planets"));
    this.entries = entries;
  }

  public static void receive(List<PlanetCatalog.Entry> entries) {
    var mc = Minecraft.getInstance();
    if (mc.screen instanceof PlanetManagerScreen s) {
      s.entries = entries;
      s.rebuildWidgets();
    } else mc.setScreen(new PlanetManagerScreen(entries));
  }

  public List<PlanetCatalog.Entry> entries() {
    return entries;
  }

  private EditBox field(int fx, int fy, int fw, String value) {
    var e = new EditBox(font, fx, fy, fw, 18, Component.literal(value));
    e.setMaxLength(24);
    e.setValue(value);
    addRenderableWidget(e);
    return e;
  }

  private Button button(String text, int bx, int by, int bw, Runnable action) {
    return addRenderableWidget(
        Button.builder(Component.literal(text), b -> action.run()).bounds(bx, by, bw, 20).build());
  }

  @Override
  protected void init() {
    int left = 14,
        right = width / 2 + 8,
        listWidth = right - left - 20,
        available = width - right - 14;
    page = Math.min(page, Math.max(0, (entries.size() - 1) / 5));
    for (int i = page * 5; i < Math.min(entries.size(), page * 5 + 5); i++) {
      var e = entries.get(i);
      button(
          e.name() + "  R=" + e.radius(),
          left,
          50 + (i % 5) * 22,
          listWidth,
          () -> selected = e.name());
    }
    button(
        "Previous",
        left,
        164,
        listWidth / 2 - 2,
        () -> {
          if (page > 0) {
            page--;
            rebuildWidgets();
          }
        });
    button(
        "Next",
        left + listWidth / 2 + 2,
        164,
        listWidth / 2 - 2,
        () -> {
          if ((page + 1) * 5 < entries.size()) {
            page++;
            rebuildWidgets();
          }
        });
    button(
        "Refresh list",
        left,
        190,
        listWidth,
        () -> PacketDistributor.sendToServer(PlanetManagerNetwork.Action.list()));
    name = field(right, 60, available, "new_planet");
    radius = field(right, 92, 60, "64");
    seed = field(right + 66, 92, available - 66, "");
    generateButton =
        button(
            "Generate new planet",
            right,
            116,
            available,
            () -> {
              try {
                int r = Integer.parseInt(radius.getValue());
                if (!PlanetCatalog.validName(name.getValue()) || r < 32 || r > 1024)
                  throw new IllegalArgumentException();
                if (entries.stream().anyMatch(e -> e.name().equals(name.getValue()))) {
                  error = "Name already exists.";
                  return;
                }
                if (!seed.getValue().isBlank()) Long.parseLong(seed.getValue());
                selected = name.getValue();
                PacketDistributor.sendToServer(
                    new PlanetManagerNetwork.Action(
                        1, name.getValue(), r, seed.getValue(), 0, 0, ""));
                error = "";
              } catch (IllegalArgumentException e) {
                error = "Check name, radius (32..1024) and seed.";
              }
            });
    int coordinateWidth = (available - 8) / 3;
    x = field(right, 168, coordinateWidth, "0");
    z = field(right + coordinateWidth + 4, 168, coordinateWidth, "0");
    heightField = field(right + 2 * (coordinateWidth + 4), 168, coordinateWidth, "surface");
    button("Teleport", right, 194, available / 2 - 2, () -> travel(2));
    button("Enter + map", right + available / 2 + 2, 194, available / 2 - 2, () -> travel(3));
    button("Back", width - 84, 10, 70, () -> onClose());
  }

  public void travel(int action) {
    try {
      double px = Double.parseDouble(x.getValue()), pz = Double.parseDouble(z.getValue());
      String h = heightField.getValue();
      if (!h.isBlank() && !h.equalsIgnoreCase("surface")) Double.parseDouble(h);
      if (!Double.isFinite(px) || !Double.isFinite(pz)) throw new NumberFormatException();
      PacketDistributor.sendToServer(
          new PlanetManagerNetwork.Action(action, selected, 32, "", px, pz, h));
      Minecraft.getInstance().setScreen(null);
    } catch (NumberFormatException e) {
      error = "Invalid teleport coordinates.";
    }
  }

  @Override
  public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

  @Override
  public void render(GuiGraphics g, int mx, int my, float pt) {
    try {
      int r = Integer.parseInt(radius.getValue());
      generateButton.setMessage(
          Component.literal(
              r >= 32 && r <= 1024
                  ? "Generate "
                      + PeriodicMath.circumference(r)
                      + "x"
                      + PeriodicMath.circumference(r)
                  : "Generate new planet"));
    } catch (NumberFormatException ignored) {
      generateButton.setMessage(Component.literal("Generate new planet"));
    }
    g.fill(0, 0, width, height, 0xFF0B101C);
    int right = width / 2 + 8;
    g.drawString(font, "PLANETS", 14, 16, 0xFFFFFF, false);
    g.drawString(font, "Saved planets · select, create and travel", 14, 34, 0xA2BAD4, false);
    g.drawString(font, "NEW PLANET", right, 46, 0xFFFFFF, false);
    g.drawString(font, "Radius / Seed (blank = random)", right, 81, 0xA2BAD4, false);
    g.drawString(font, "Selected: " + selected, right, 143, 0xF0D078, false);
    g.drawString(font, "X / Z / Y or surface", right, 155, 0xA2BAD4, false);
    g.drawString(
        font,
        "New planets are independent; existing terrain is preserved.",
        14,
        height - 29,
        0xA2BAD4,
        false);
    g.drawString(
        font,
        error.isEmpty() ? "Up to 16 generated planets per world." : error,
        14,
        height - 16,
        error.isEmpty() ? 0xA2BAD4 : 0xFF7777,
        false);
    super.render(g, mx, my, pt);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
