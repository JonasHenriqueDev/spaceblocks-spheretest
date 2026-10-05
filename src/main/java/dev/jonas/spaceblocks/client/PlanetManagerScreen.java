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
  private PlanetType newType = PlanetType.EARTH;
  private boolean icePoles = true;
  private Button typeButton, polesButton;

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
    boolean compact = height < 300;
    int left = 14,
        right = width / 2 + 8,
        listWidth = right - left - 20,
        available = width - right - 14;
    page = Math.min(page, Math.max(0, (entries.size() - 1) / 5));
    for (int i = page * 5; i < Math.min(entries.size(), page * 5 + 5); i++) {
      var e = entries.get(i);
      button(
          e.name() + "  R=" + e.radius() + " " + e.type().id(),
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
    name = field(right, compact ? 46 : 60, available, "new_planet");
    radius = field(right, compact ? 74 : 92, 60, "64");
    seed = field(right + 66, compact ? 74 : 92, available - 66, "");
    typeButton =
        button(
            "Type: " + newType.id(),
            right,
            compact ? 98 : 116,
            available / 2 - 2,
            () -> {
              var types = PlanetType.values();
              newType = types[newType.ordinal() + 1 == types.length ? 1 : newType.ordinal() + 1];
              icePoles = newType.defaultPoles;
              typeButton.setMessage(Component.literal("Type: " + newType.id()));
              polesButton.setMessage(Component.literal("Ice poles: " + (icePoles ? "ON" : "OFF")));
              polesButton.active = newType != PlanetType.NETHER;
            });
    polesButton =
        button(
            "Ice poles: " + (icePoles ? "ON" : "OFF"),
            right + available / 2 + 2,
            compact ? 98 : 116,
            available / 2 - 2,
            () -> {
              icePoles = !icePoles;
              polesButton.setMessage(Component.literal("Ice poles: " + (icePoles ? "ON" : "OFF")));
            });
    polesButton.active = newType != PlanetType.NETHER;
    generateButton =
        button(
            "Generate new planet",
            right,
            compact ? 122 : 142,
            available,
            () -> {
              try {
                int r = Integer.parseInt(radius.getValue());
                if (!PlanetCatalog.validName(name.getValue()) || r < 32 || r > 128)
                  throw new IllegalArgumentException();
                if (entries.stream().anyMatch(e -> e.name().equals(name.getValue()))) {
                  error = "Name already exists.";
                  return;
                }
                if (!seed.getValue().isBlank()) Long.parseLong(seed.getValue());
                selected = name.getValue();
                PacketDistributor.sendToServer(
                    new PlanetManagerNetwork.Action(
                        1, name.getValue(), r, seed.getValue(), 0, 0, "", newType.id(), icePoles));
                error = "";
              } catch (IllegalArgumentException e) {
                error = "Check name, radius (32..128) and seed.";
              }
            });
    int coordinateWidth = (available - 8) / 3;
    x = field(right, compact ? 184 : 206, coordinateWidth, "0");
    z = field(right + coordinateWidth + 4, compact ? 184 : 206, coordinateWidth, "0");
    heightField =
        field(right + 2 * (coordinateWidth + 4), compact ? 184 : 206, coordinateWidth, "surface");
    button("Teleport", right, compact ? 208 : 232, available / 2 - 2, () -> travel(2));
    button(
        "Enter + map",
        right + available / 2 + 2,
        compact ? 208 : 232,
        available / 2 - 2,
        () -> travel(3));
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
    boolean compact = height < 300;
    try {
      int r = Integer.parseInt(radius.getValue());
      generateButton.setMessage(
          Component.literal(
              r >= 32 && r <= 128
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
    g.drawString(font, "Saved planets", 14, 34, 0xA2BAD4, false);
    g.drawString(font, "NEW PLANET", right, compact ? 32 : 46, 0xFFFFFF, false);
    g.drawString(font, "Radius / Seed (blank = random)", right, compact ? 63 : 81, 0xA2BAD4, false);
    g.drawString(font, "Selected: " + selected, right, compact ? 146 : 172, 0xF0D078, false);
    entries.stream()
        .filter(e -> e.name().equals(selected))
        .findFirst()
        .ifPresent(
            e ->
                g.drawString(
                    font,
                    e.type().id() + " · ice poles " + (e.poles() ? "ON" : "OFF"),
                    right,
                    compact ? 158 : 182,
                    0xA2BAD4,
                    false));
    g.drawString(font, "X / Z / Y or surface", right, compact ? 172 : 192, 0xA2BAD4, false);
    if (!compact)
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
        height - (compact ? 12 : 16),
        error.isEmpty() ? 0xA2BAD4 : 0xFF7777,
        false);
    super.render(g, mx, my, pt);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
