package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.SpaceBlocks;
import java.nio.file.*;
import java.util.Properties;
import net.minecraft.client.Minecraft;

public final class PlanetHudConfig {
  public static boolean enabled = true, transition = true;
  public static int size = 96, corner = 0, marginX = 8, marginY = 8, duration = 1200;
  private static boolean loaded;

  private static Path path() {
    return Minecraft.getInstance()
        .gameDirectory
        .toPath()
        .resolve("config/spaceblocks-hud.properties");
  }

  public static void load() {
    if (loaded) return;
    loaded = true;
    if (!Files.exists(path())) return;
    try (var in = Files.newInputStream(path())) {
      var p = new Properties();
      p.load(in);
      enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
      transition = Boolean.parseBoolean(p.getProperty("transition", "true"));
      size = number(p, "size", 96, 64, 192);
      corner = number(p, "corner", 0, 0, 3);
      marginX = number(p, "margin_x", 8, 0, 200);
      marginY = number(p, "margin_y", 8, 0, 200);
      duration = number(p, "transition_ms", 1200, 400, 2400);
    } catch (Exception e) {
      SpaceBlocks.LOGGER.warn("Cannot load HUD settings", e);
    }
  }

  private static int number(Properties p, String key, int fallback, int min, int max) {
    try {
      return Math.clamp(Integer.parseInt(p.getProperty(key)), min, max);
    } catch (Exception e) {
      return fallback;
    }
  }

  public static void save() {
    var p = new Properties();
    p.setProperty("enabled", "" + enabled);
    p.setProperty("transition", "" + transition);
    p.setProperty("size", "" + size);
    p.setProperty("corner", "" + corner);
    p.setProperty("margin_x", "" + marginX);
    p.setProperty("margin_y", "" + marginY);
    p.setProperty("transition_ms", "" + duration);
    try {
      Files.createDirectories(path().getParent());
      try (var out = Files.newOutputStream(path())) {
        p.store(out, "Space Blocks client HUD");
      }
    } catch (Exception e) {
      SpaceBlocks.LOGGER.warn("Cannot save HUD settings", e);
    }
  }
}
