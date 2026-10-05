package dev.jonas.spaceblocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;

public final class PeriodicBiomeSource extends BiomeSource {
  public static final MapCodec<PeriodicBiomeSource> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      Codec.INT.fieldOf("radius").forGetter(s -> s.radius),
                      Biome.CODEC.listOf().fieldOf("biomes").forGetter(s -> s.biomes))
                  .apply(i, PeriodicBiomeSource::new));
  private int radius;
  private Long explicitSeed;
  private final List<Holder<Biome>> biomes;
  private volatile PeriodicTerrain climate;

  public void configure(int radius, long seed) {
    this.radius = radius;
    explicitSeed = seed;
    climate = null;
  }

  public PeriodicBiomeSource(int radius, List<Holder<Biome>> biomes) {
    if (biomes.size() != 7) throw new IllegalArgumentException("Seven periodic biomes required");
    this.radius = radius;
    this.biomes = List.copyOf(biomes);
  }

  @Override
  protected Stream<Holder<Biome>> collectPossibleBiomes() {
    return biomes.stream();
  }

  @Override
  protected MapCodec<? extends BiomeSource> codec() {
    return CODEC;
  }

  @Override
  public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
    var map = climate;
    if (map == null)
      climate =
          map =
              new PeriodicTerrain(
                  PeriodicMath.circumference(radius),
                  explicitSeed == null ? PeriodicTerrain.seed(sampler) : explicitSeed);
    return biomes.get(map.biome(x * 4, z * 4));
  }
}
