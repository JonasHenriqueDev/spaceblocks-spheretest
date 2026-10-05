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
    public static final String MOD_ID="spaceblocks";
    public static final Logger LOGGER=LogUtils.getLogger();
    public static final ResourceKey<Level> SMALL=ResourceKey.create(Registries.DIMENSION,id("periodic_small")),LARGE=ResourceKey.create(Registries.DIMENSION,id("periodic_large"));
    public static final PlanetSettings clientSettings=new PlanetSettings();
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS=DeferredRegister.create(Registries.CHUNK_GENERATOR,MOD_ID);
    static{GENERATORS.register("periodic",()->PlanetGenerator.CODEC);}
    public SpaceBlocks(IEventBus bus){GENERATORS.register(bus);bus.addListener(PlanetNetwork::register);NeoForge.EVENT_BUS.addListener(PlanetCommands::register);NeoForge.EVENT_BUS.addListener(PlanetServer::tick);NeoForge.EVENT_BUS.addListener(PlanetServer::stopped);NeoForge.EVENT_BUS.addListener(PlanetTests::started);LOGGER.info("Space Blocks 0.6.0: Jeija/Spheretest periodic flat map and camera-relative exponential projection");}
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(MOD_ID,path);}
}
