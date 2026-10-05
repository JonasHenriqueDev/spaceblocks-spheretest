package dev.jonas.spaceblocks;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(SpaceBlocks.MOD_ID)
public final class SpaceBlocks {
  public static final String MOD_ID = "spaceblocks";
  public static final Logger LOGGER = LogUtils.getLogger();
  public static final ResourceKey<Level>
      SMALL = ResourceKey.create(Registries.DIMENSION, id("periodic_small")),
      LARGE = ResourceKey.create(Registries.DIMENSION, id("periodic_large")),
      NATURAL = ResourceKey.create(Registries.DIMENSION, id("periodic_natural")),
      LAB = ResourceKey.create(Registries.DIMENSION, id("planet_lab"));
  public static final PlanetSettings clientSettings = new PlanetSettings();
  private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
      DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);
  private static final DeferredRegister<
          MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>>
      BIOMES = DeferredRegister.create(Registries.BIOME_SOURCE, MOD_ID);

  static {
    GENERATORS.register("periodic", () -> PlanetGenerator.CODEC);
    BIOMES.register("periodic", () -> PeriodicBiomeSource.CODEC);
  }

  public SpaceBlocks(IEventBus bus) {
    GENERATORS.register(bus);
    BIOMES.register(bus);
    bus.addListener(PlanetNetwork::register);
    NeoForge.EVENT_BUS.addListener(PlanetCommands::register);
    NeoForge.EVENT_BUS.addListener(PlanetServer::tick);
    NeoForge.EVENT_BUS.addListener(PlanetServer::stopped);
    NeoForge.EVENT_BUS.addListener(PlanetAtlas::tick);
    NeoForge.EVENT_BUS.addListener(PlanetTunnel::tick);
    NeoForge.EVENT_BUS.addListener(PlanetTunnel::stopped);
    NeoForge.EVENT_BUS.addListener(PlanetAtlas::stopped);
    NeoForge.EVENT_BUS.addListener(PlanetCatalog::started);
    NeoForge.EVENT_BUS.addListener(PlanetLab::tick);
    NeoForge.EVENT_BUS.addListener(PlanetLab::stopped);
    NeoForge.EVENT_BUS.addListener(PlanetSatellite::tick);
    NeoForge.EVENT_BUS.addListener(PlanetSatellite::stopped);
    NeoForge.EVENT_BUS.addListener(PlanetSatellite::started);
    NeoForge.EVENT_BUS.addListener(PlanetServer::dimensionChanged);
    NeoForge.EVENT_BUS.addListener(PlanetTests::started);
    NeoForge.EVENT_BUS.addListener(PlanetNetworkTests::joined);
    LOGGER.info(
        "Space Blocks 0.8.2: Jeija/Spheretest periodic terrain and camera-relative exponential"
            + " projection");
  }

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
  }
}
