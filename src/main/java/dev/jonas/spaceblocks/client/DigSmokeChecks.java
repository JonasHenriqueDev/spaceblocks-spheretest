package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.surface.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

/** Exercises the real attack key and renderer below the surface in both test sizes. */
public final class DigSmokeChecks {
    private static int ticks,stage,at,world;
    private static volatile String failure;
    public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("spaceblocks.digSmoke"))return;
        var m=Minecraft.getInstance();ticks++;m.options.pauseOnLostFocus=false;
        if(ticks>2000){finish("FAIL: timeout stage="+stage+" y="+(m.player==null?"?":m.player.getY()));m.options.keyAttack.setDown(false);m.stop();return;}
        if(stage==5){if(ticks-at==4)command("planet leave");if(ticks-at>40)m.stop();return;}
        if(m.player==null||m.level==null)return;
        if(stage==0&&ticks>60){
            command(world==0?"planet":"planet small");stage=1;at=ticks;
            var server=m.getSingleplayerServer();var id=m.player.getUUID();
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);var g=FlatMotion.generator(p.serverLevel());var d=g.surface();
                if(d.faceSize()!=(world==0?1024:64))failure="Wrong command destination";
                var start=d.storage(new Position(Face.FRONT,d.faceSize()*.5+8.5,d.faceSize()*.5+8.5),d.height(new Position(Face.FRONT,d.faceSize()*.5+8.5,d.faceSize()*.5+8.5))+1);
                p.serverLevel().getChunk((int)start.x>>4,(int)start.z>>4);
                // Restore a fresh column only in the disposable development world.
                for(int y=(int)start.y-25;y<start.y;y++)p.serverLevel().setBlock(new net.minecraft.core.BlockPos((int)start.x,y,(int)start.z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                p.setGameMode(GameType.CREATIVE);p.connection.teleport(start.x,start.y,start.z,0,90);p.setDeltaMovement(Vec3.ZERO);
            });return;
        }
        if(!FlatClient.active())return;
        if(stage==1&&ticks-at>120){m.options.keyAttack.setDown(true);stage=2;at=ticks;}
        if(stage==2&&m.player.getY()<FlatClient.surface.height(FlatClient.surface.locate(m.player.getX(),m.player.getZ()))-12){
            m.options.keyAttack.setDown(false);
            if(m.player.isNoGravity()||!m.player.isAlive())failure="Gravity or player state mismatch";
            FlatSmokeChecks.requestShot(world==0?"dig-large-underground.png":"dig-small-underground.png");
            SpaceBlocks.LOGGER.info("DIG_UNDERGROUND_OK: size={} feetY={} cameraY={}",FlatClient.surface.faceSize(),m.player.getY(),m.gameRenderer.getMainCamera().getPosition().y);
            stage=3;at=ticks;
        }
        if(stage==3&&ticks-at>50){
            var server=m.getSingleplayerServer();var id=m.player.getUUID();
            server.execute(()->{var p=server.getPlayerList().getPlayer(id);if(!p.serverLevel().getBlockState(p.blockPosition().above(12)).isAir())failure="Mining did not remove the column";});
            command("planet surface");stage=4;at=ticks;
        }
        if(stage==4&&ticks-at>80){
            if(m.player.getY()<FlatClient.surface.height(FlatClient.surface.locate(m.player.getX(),m.player.getZ()))+1)failure="Surface command failed";
            if(world++==0){stage=0;return;}
            finish(failure==null?"PASS: /planet and /planet small; real downward mining over 12 blocks below the terrain in both sizes; underground rendering, normal gravity, surface return, no crash.":"FAIL: "+failure);stage=5;at=ticks;
        }
    }
    private static void command(String text){var m=Minecraft.getInstance();var s=m.getSingleplayerServer();var id=m.player.getUUID();s.execute(()->{var p=s.getPlayerList().getPlayer(id);s.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),text);});}
    private static void finish(String text){try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("dig-client-result.txt"),text+"\n");SpaceBlocks.LOGGER.info("DIG_RESULT: {}",text);}catch(Exception e){throw new RuntimeException(e);}}
}
