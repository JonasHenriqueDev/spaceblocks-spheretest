package dev.jonas.spaceblocks;

/** Seeded C2 continuous toroidal value noise. Every octave tiles in both horizontal axes. */
public final class PeriodicTerrain {
  private final int size;
  private final long seed;

  public PeriodicTerrain(int size, long seed) {
    this.size = size;
    this.seed = seed;
  }

  public static long seed(net.minecraft.world.level.biome.Climate.Sampler sampler) {
    var p = sampler.sample(0, 0, 0);
    return hash(
        p.temperature() ^ p.humidity(),
        (int) p.continentalness(),
        (int) p.erosion(),
        (int) p.weirdness());
  }

  private static double fade(double t) {
    return t * t * t * (t * (t * 6 - 15) + 10);
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  public static long hash(long seed, int x, int y, int z) {
    long n =
        seed ^ (x * 0x632BE59BD9B4E019L) ^ (y * 0x9E3779B97F4A7C15L) ^ (z * 0xC6BC279692B5CC83L);
    n = (n ^ (n >>> 30)) * 0xBF58476D1CE4E5B9L;
    n = (n ^ (n >>> 27)) * 0x94D049BB133111EBL;
    return n ^ (n >>> 31);
  }

  private double value(int x, int y, int z, int cells, long salt) {
    return (hash(seed + salt, Math.floorMod(x, cells), y, Math.floorMod(z, cells)) >>> 11)
            * 0x1.0p-53
            * 2
        - 1;
  }

  public double noise(double x, double y, double z, int cells, double verticalScale, long salt) {
    double u = PeriodicMath.wrap(x, size) * cells / size,
        v = y / verticalScale,
        w = PeriodicMath.wrap(z, size) * cells / size;
    int a = (int) Math.floor(u), b = (int) Math.floor(v), c = (int) Math.floor(w);
    double tx = fade(u - a), ty = fade(v - b), tz = fade(w - c);
    double low =
        lerp(
            lerp(value(a, b, c, cells, salt), value(a + 1, b, c, cells, salt), tx),
            lerp(value(a, b, c + 1, cells, salt), value(a + 1, b, c + 1, cells, salt), tx),
            tz);
    double high =
        lerp(
            lerp(value(a, b + 1, c, cells, salt), value(a + 1, b + 1, c, cells, salt), tx),
            lerp(value(a, b + 1, c + 1, cells, salt), value(a + 1, b + 1, c + 1, cells, salt), tx),
            tz);
    return lerp(low, high, ty);
  }

  public double climate(int x, int z, long salt) {
    return noise(x, 0, z, 4, 1, salt);
  }

  public int surface(int x, int z) {
    double continents = noise(x, 0, z, 4, 1, 1),
        hills = noise(x, 0, z, 16, 1, 2),
        detail = noise(x, 0, z, 64, 1, 3);
    double mountains = Math.pow(Math.max(0, noise(x, 0, z, 8, 1, 4)), 2);
    double scale = Math.min(1, size / 1200.0);
    double height = 64 + scale * (continents * 44 + hills * 16 + detail * 4 + mountains * 85);
    double river = Math.abs(noise(x, 0, z, 8, 1, 5));
    if (height > 60 && height < 110 && river < .065) height = lerp(61, height, fade(river / .065));
    return (int) Math.round(height);
  }

  public boolean cave(int x, int y, int z, int surface) {
    if (y > surface - 5 || y < -490) return false;
    int cells = Math.max(8, size / 32);
    double a = noise(x, y, z, cells, 30, 21), b = noise(x, y, z, cells, 30, 22);
    return (Math.abs(a) < .13 && Math.abs(b) < .13)
        || noise(x, y, z, Math.max(4, size / 80), 42, 23) > .52;
  }

  public int biome(int x, int z) {
    if (surface(x, z) < 60) return 0; // ocean
    double temperature = climate(x, z, 31), humidity = climate(x, z, 32);
    if (temperature < -.27) return 5; // snowy plains
    if (surface(x, z) > 104) return 6; // mountains
    if (temperature > .24 && humidity < .05) return 3; // desert
    if (temperature < -.08) return 4; // taiga
    if (humidity > -.1) return 2; // forest
    return 1; // plains
  }
}
