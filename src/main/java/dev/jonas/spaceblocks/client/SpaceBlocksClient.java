package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.PlanetDefinition;
import dev.jonas.spaceblocks.PlanetChunkGenerator;
import dev.jonas.spaceblocks.SpaceBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import dev.jonas.spaceblocks.physics.RadialMotion;

@Mod(value = SpaceBlocks.MOD_ID, dist = Dist.CLIENT)
public final class SpaceBlocksClient {
    public SpaceBlocksClient(IEventBus bus) {
        bus.addListener(SpaceBlocksClient::effects);
        bus.addListener((net.neoforged.neoforge.client.event.RegisterShadersEvent event) -> { try { FlatShaders.register(event); } catch(java.io.IOException exception) { throw new RuntimeException(exception); } });
        bus.addListener(SpaceBlocksClient::hud);
        NeoForge.EVENT_BUS.addListener(SpaceBlocksClient::fog);
        NeoForge.EVENT_BUS.addListener(ClientSmokeChecks::tick);
        NeoForge.EVENT_BUS.addListener(ClientRadialMotion::tick);
        NeoForge.EVENT_BUS.addListener(RadialWalkSmoke::tick);
        NeoForge.EVENT_BUS.addListener(InteractionSmokeChecks::tick);
        NeoForge.EVENT_BUS.addListener(FlatSmokeChecks::tick);
        NeoForge.EVENT_BUS.addListener(FlatSmokeChecks::frame);
        NeoForge.EVENT_BUS.addListener(ColorsSmokeChecks::tick);
        NeoForge.EVENT_BUS.addListener(DigSmokeChecks::tick);
        NeoForge.EVENT_BUS.addListener(PortalRenderer::frame);
        NeoForge.EVENT_BUS.addListener(OrbitBuildRenderer::frame);
        NeoForge.EVENT_BUS.addListener(PortalSmokeChecks::tick);
    }

    private static void effects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(SpaceBlocks.id("space"), new SpaceEffects());
        event.register(SpaceBlocks.id("flat"), new SpaceEffects());
    }

    private static void hud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(SpaceBlocks.id("flight_status"), (graphics, delta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null
                    || (!SpaceBlocks.isSpace(minecraft.level.dimension()) && !SpaceBlocks.isFlat(minecraft.level.dimension()))
                    || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) return;
            boolean flat=SpaceBlocks.isFlat(minecraft.level.dimension());
            boolean radial=RadialMotion.active(minecraft.player);
            graphics.drawString(minecraft.font, "SPACE BLOCKS 0.5 | "+(flat?"Mapa em cruz · gravidade normal":radial?"Gravidade radial":"Voo livre"), 8, 8, 0xB5E4FF, true);
            graphics.drawString(minecraft.font, (flat&&!minecraft.player.getAbilities().flying)||radial?"WASD: andar | Espaço: pular | Ctrl: correr":"WASD: voar | Espaço/Shift: subir/descer", 8, 20, 0xFFFFFF, true);
            graphics.drawString(minecraft.font, "/planet walk | /planet fly | /planet leave", 8, 32, 0xFFFFFF, true);
            if(flat&&FlatClient.surface!=null&&FlatClient.surface.coloredFaces()) {
                var p=FlatClient.surface.locate(minecraft.player.getX(),minecraft.player.getZ());
                if(p!=null)graphics.drawString(minecraft.font,"Teste de cores: "+dev.jonas.spaceblocks.surface.FlatDefinition.faceName(p.face()),8,44,dev.jonas.spaceblocks.surface.FlatDefinition.faceColor(p.face()),true);
            }
        });
    }

    private static void fog(ViewportEvent.RenderFog event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && (SpaceBlocks.isSpace(minecraft.level.dimension()) || SpaceBlocks.isFlat(minecraft.level.dimension()))
                && event.getType() == FogType.NONE) {
            event.setNearPlaneDistance(100_000);
            event.setFarPlaneDistance(1_000_000);
            event.setCanceled(true);
        }
    }
}

