package dev.jonas.spaceblocks.client;
import dev.jonas.spaceblocks.SpaceBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
@Mod(value=SpaceBlocks.MOD_ID,dist=Dist.CLIENT)
public final class SpaceBlocksClient {
    public SpaceBlocksClient(IEventBus bus){bus.addListener((RegisterShadersEvent e)->{try{PlanetShaders.register(e);}catch(java.io.IOException ex){throw new RuntimeException(ex);}});bus.addListener((RegisterDimensionSpecialEffectsEvent e)->e.register(SpaceBlocks.id("periodic"),new DimensionSpecialEffects(Float.NaN,false,DimensionSpecialEffects.SkyType.NONE,false,true){@Override public Vec3 getBrightnessDependentFogColor(Vec3 c,float b){return Vec3.ZERO;}@Override public boolean isFoggyAt(int x,int z){return false;}}));NeoForge.EVENT_BUS.addListener(PeriodicRenderer::frame);NeoForge.EVENT_BUS.addListener(PeriodicRenderer::stage);NeoForge.EVENT_BUS.addListener(PlanetClientTests::tick);NeoForge.EVENT_BUS.addListener(PlanetClientTests::frame);NeoForge.EVENT_BUS.addListener((ViewportEvent.RenderFog e)->{if(PlanetClient.active()){e.setNearPlaneDistance(100000);e.setFarPlaneDistance(100001);e.setCanceled(true);}});}
}
