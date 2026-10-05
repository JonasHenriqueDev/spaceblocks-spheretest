package dev.jonas.spaceblocks;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.mojang.serialization.MapCodec;
import org.slf4j.Logger;

@Mod(SpaceBlocks.MOD_ID)
public final class SpaceBlocks {
    public static final String MOD_ID = "spaceblocks";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION, id("space_large"));
    public static final ResourceKey<Level> FLAT = ResourceKey.create(Registries.DIMENSION, id("flat_planet"));
    public static final ResourceKey<Level> FLAT_TEST = ResourceKey.create(Registries.DIMENSION, id("flat_test_v2"));
    public static final ResourceKey<Level> FLAT_COLORS = ResourceKey.create(Registries.DIMENSION, id("flat_colors"));
    public static final ResourceKey<Level> FLAT_TERRAIN = ResourceKey.create(Registries.DIMENSION, id("flat_terrain"));
    public static boolean isFlat(ResourceKey<Level> d) { return d.equals(FLAT) || d.equals(FLAT_TEST) || d.equals(FLAT_COLORS) || d.equals(FLAT_TERRAIN); }
    public static final ResourceKey<Level> LEGACY_SPACE = ResourceKey.create(Registries.DIMENSION, id("space"));
    public static boolean isSpace(ResourceKey<Level> dimension) { return dimension.equals(SPACE) || dimension.equals(LEGACY_SPACE); }
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);

    static {
        GENERATORS.register("planet", () -> PlanetChunkGenerator.CODEC);
        GENERATORS.register("flat_planet", () -> dev.jonas.spaceblocks.surface.FlatChunkGenerator.CODEC);
    }

    public SpaceBlocks(IEventBus bus) {
        GENERATORS.register(bus);
        bus.addListener(dev.jonas.spaceblocks.network.RadialNetwork::register);
        bus.addListener(dev.jonas.spaceblocks.network.FlatNetwork::register);
        NeoForge.EVENT_BUS.addListener(dev.jonas.spaceblocks.surface.FlatMotion::started);
        NeoForge.EVENT_BUS.addListener(dev.jonas.spaceblocks.surface.FlatMotion::serverTick);
        NeoForge.EVENT_BUS.addListener(dev.jonas.spaceblocks.surface.FlatMotion::place);
        NeoForge.EVENT_BUS.addListener(SpaceCommands::register);
        NeoForge.EVENT_BUS.addListener(SpaceCommands::tick);
        NeoForge.EVENT_BUS.addListener(SmokeChecks::serverStarted);
        NeoForge.EVENT_BUS.addListener(dev.jonas.spaceblocks.physics.RadialMotion::serverTick);
        NeoForge.EVENT_BUS.addListener(dev.jonas.spaceblocks.physics.RadialMotion::login);
        LOGGER.info("Space Blocks 0.4: flat cube-net surface, connected charts and visual spherical projection.");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
