package dev.jonas.spaceblocks;

import java.util.Locale;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** Stable saved preset IDs. Legacy keeps existing procedural saves on their old generator. */
public enum PlanetType {
  LEGACY(Biomes.PLAINS, false),
  EARTH(Biomes.PLAINS, true),
  DESERT(Biomes.DESERT, false),
  JUNGLE(Biomes.JUNGLE, false),
  MUSHROOM(Biomes.MUSHROOM_FIELDS, false),
  DIRT(Biomes.STONY_PEAKS, true),
  STONE(Biomes.STONY_PEAKS, false),
  NETHER(Biomes.NETHER_WASTES, false),
  FLAT(Biomes.PLAINS, false);

  public final ResourceKey<Biome> biome;
  public final boolean defaultPoles;

  PlanetType(ResourceKey<Biome> biome, boolean defaultPoles) {
    this.biome = biome;
    this.defaultPoles = defaultPoles;
  }

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static PlanetType parse(String name) {
    return valueOf(name.toUpperCase(Locale.ROOT));
  }

  public boolean polar(int z, int width, boolean enabled) {
    return enabled && Math.abs(PeriodicMath.wrap(z, width)) >= width * .36;
  }
}
