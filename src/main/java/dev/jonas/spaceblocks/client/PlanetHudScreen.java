package dev.jonas.spaceblocks.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PlanetHudScreen extends Screen {
  private final Screen parent;
  private static final String[] CORNERS = {"Top right", "Top left", "Bottom right", "Bottom left"};

  public PlanetHudScreen(Screen parent) {
    super(Component.literal("Planet HUD settings"));
    this.parent = parent;
  }

  private void button(int y, String text, Runnable action) {
    addRenderableWidget(
        Button.builder(
                Component.literal(text),
                b -> {
                  action.run();
                  PlanetHudConfig.save();
                  if (minecraft.screen == this) rebuildWidgets();
                })
            .bounds(width / 2 - 115, y, 230, 20)
            .build());
  }

  @Override
  protected void init() {
    PlanetHudConfig.load();
    int y = Math.max(30, height / 2 - 100);
    button(
        y,
        "Globe HUD: " + (PlanetHudConfig.enabled ? "ON" : "OFF"),
        () -> PlanetHudConfig.enabled = !PlanetHudConfig.enabled);
    button(
        y + 24,
        "Size: " + PlanetHudConfig.size,
        () -> PlanetHudConfig.size = PlanetHudConfig.size >= 192 ? 64 : PlanetHudConfig.size + 16);
    button(
        y + 48,
        "Position: " + CORNERS[PlanetHudConfig.corner],
        () -> PlanetHudConfig.corner = (PlanetHudConfig.corner + 1) % 4);
    button(
        y + 72,
        "Horizontal margin: " + PlanetHudConfig.marginX,
        () -> PlanetHudConfig.marginX = (PlanetHudConfig.marginX + 8) % 104);
    button(
        y + 96,
        "Vertical margin: " + PlanetHudConfig.marginY,
        () -> PlanetHudConfig.marginY = (PlanetHudConfig.marginY + 8) % 104);
    button(
        y + 120,
        "Crossing camera: " + (PlanetHudConfig.transition ? "ON" : "OFF"),
        () -> PlanetHudConfig.transition = !PlanetHudConfig.transition);
    button(
        y + 144,
        "Transition: " + PlanetHudConfig.duration + " ms",
        () ->
            PlanetHudConfig.duration =
                PlanetHudConfig.duration >= 2400 ? 400 : PlanetHudConfig.duration + 200);
    button(y + 168, "Done", this::onClose);
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    super.render(g, x, y, partial);
    g.drawCenteredString(font, "PLANET HUD", width / 2, 12, 0xFFFFFF);
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
