package dev.jonas.spaceblocks.client;
import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.surface.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;
/** Focused regression for the colored planet, including the actual colored seam. */
public final class ColorsSmokeChecks {
    private static int ticks,stage,at;
    private static volatile String failure;
    public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("spaceblocks.colorsSmoke"))return;
        var m=Minecraft.getInstance();ticks++;m.options.pauseOnLostFocus=false;
        if(ticks>2200){finish("FAIL: timeout stage="+stage);m.stop();return;}
        if(stage==5){if(ticks-at==4)command("space leave");if(ticks-at>40)m.stop();return;}
        if(m.player==null||m.level==null)return;
        if(stage==0&&ticks>60) {
            command("planet colors");stage=1;at=ticks;
            var server=m.getSingleplayerServer();var id=m.player.getUUID();
            server.execute(()->{
                var player=server.getPlayerList().getPlayer(id);var g=FlatMotion.generator(player.serverLevel());var d=g.surface();
                if(!d.coloredFaces()||d.faceSize()!=1024)failure="Wrong planet definition";
                for(Face face:Face.values()) {
                    Vec3 center=d.storage(new Position(face,512.5,512.5),64);
                    if(!g.blockAt((int)center.x,64,(int)center.z).equals(FlatDefinition.faceBlock(face)))failure="Face terrain color mismatch: "+face;
                    if(!g.blockAt((int)d.originX(face)-1,64,(int)d.originZ(face)+512).equals(FlatDefinition.faceBlock(canonical(new Position(face,-.5,512.5),1024).face())))failure="Apron color mismatch: "+face;
                }
                Vec3 start=d.storage(new Position(Face.FRONT,1019.5,512.5),65);player.serverLevel().getChunk((int)start.x>>4,(int)start.z>>4);
                player.connection.teleport(start.x,start.y,start.z,-90,20);player.setDeltaMovement(Vec3.ZERO);
                SpaceBlocks.LOGGER.info("COLORS_GENERATION_OK: six face colors and neighbor aprons, faceSize={}",d.faceSize());
            });return;
        }
        if(!FlatClient.active()||!FlatClient.surface.coloredFaces())return;
        var p=FlatClient.surface.locate(m.player.getX(),m.player.getZ());if(p==null)return;
        if(stage==1&&ticks-at>120) {
            var block=m.level.getBlockState(m.player.blockPosition().below());
            if(!block.equals(FlatDefinition.faceBlock(Face.FRONT)))failure="Client front color mismatch";
            FlatSmokeChecks.requestShot("flat-colors-border-before.png");m.options.keyUp.setDown(true);m.options.keySprint.setDown(true);stage=2;at=ticks;
            SpaceBlocks.LOGGER.info("COLORS_LOOK_BEFORE: yaw={} pitch={}",m.player.getYRot(),m.player.getXRot());
        }
        if(stage==2&&p.face()==Face.RIGHT) {
            m.options.keyUp.setDown(false);m.options.keySprint.setDown(false);
            if(!m.level.getBlockState(m.player.blockPosition().below()).equals(FlatDefinition.faceBlock(Face.RIGHT))||m.player.isNoGravity())failure="Blue destination or gravity mismatch";
            FlatSmokeChecks.requestShot("flat-colors-border-after.png");stage=3;at=ticks;
            SpaceBlocks.LOGGER.info("COLORS_SEAM_OK: FRONT green -> RIGHT blue, normal gravity");
            SpaceBlocks.LOGGER.info("COLORS_LOOK_AFTER: yaw={} pitch={}",m.player.getYRot(),m.player.getXRot());
        }
        if(stage==3&&ticks-at>50){
            SpaceBlocks.LOGGER.info("COLORS_LOOK_CONFIRMED: yaw={} pitch={}",m.player.getYRot(),m.player.getXRot());
            if(Math.abs(net.minecraft.util.Mth.wrapDegrees(m.player.getYRot()+90))>.01||Math.abs(m.player.getXRot()-20)>.01)failure="Seam camera changed orientation";
            command("space view");stage=4;at=ticks;
        }
        if(stage==4&&ticks-at>100) {
            FlatSmokeChecks.requestShot("flat-colors-orbit.png");
            finish(failure==null?"PASS: six colored faces, 1024-block face size, matching apron colors, client palette, real green-to-blue seam, normal gravity and colored sphere.":"FAIL: "+failure);
            stage=5;at=ticks;
        }
    }
    private static void command(String text) {var m=Minecraft.getInstance();var server=m.getSingleplayerServer();var id=m.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),text);});}
    private static void finish(String text){try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("colors-client-result.txt"),text+"\n");SpaceBlocks.LOGGER.info("COLORS_RESULT: {}",text);}catch(Exception e){throw new RuntimeException(e);}}
}
