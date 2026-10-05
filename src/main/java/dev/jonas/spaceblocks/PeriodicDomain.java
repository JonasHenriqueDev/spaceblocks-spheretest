package dev.jonas.spaceblocks;

/** Tile a scalar field smoothly in both horizontal directions without game registry state. */
public final class PeriodicDomain {
  @FunctionalInterface
  public interface Sample {
    double at(int x, int y, int z);
  }

  private static double fade(double t) {
    return t * t * t * (t * (t * 6 - 15) + 10);
  }

  public static double sample(Sample source, int x, int y, int z, int width) {
    int u = Math.floorMod(x + width / 2, width), v = Math.floorMod(z + width / 2, width);
    double tx = fade(u / (double) width), tz = fade(v / (double) width);
    double a = source.at(u, y, v), b = source.at(u - width, y, v);
    double c = source.at(u, y, v - width), d = source.at(u - width, y, v - width);
    return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz;
  }
}
