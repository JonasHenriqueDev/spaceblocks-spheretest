package dev.jonas.spaceblocks.client;
import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.surface.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;
/** Real GPU regression: remote building, live modification, crossing, orbit and relief. */
public final class PortalSmokeChecks {
    private static Object previousMesh;
    private static int ticks,stage,at,before;private static volatile String failure;
    public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("spaceblocks.portalSmoke"))return;var m=Minecraft.getInstance();ticks++;m.options.pauseOnLostFocus=false;
        if(ticks>2400){finish("FAIL: portal test timeout stage="+stage);m.options.keyUp.setDown(false);m.stop();return;}
        if(stage==8){if(ticks-at==4)command("planet leave");if(ticks-at>40)m.stop();return;}
        if(m.player==null||m.level==null)return;
        if(stage==0&&ticks>60){
            command("planet colors");stage=1;at=ticks;var server=m.getSingleplayerServer();var id=m.player.getUUID();
            server.execute(()->{var p=server.getPlayerList().getPlayer(id);var d=FlatMotion.generator(p.serverLevel()).surface();
                for(int x=20;x<28;x++)for(int z=508;z<516;z++)for(int y=65;y<83;y++)p.serverLevel().setBlock(BlockPos.containing(d.storage(new Position(Face.RIGHT,x+.5,z+.5),y)),Blocks.GOLD_BLOCK.defaultBlockState(),3);
                Vec3 start=d.storage(new Position(Face.FRONT,1011.5,512.5),65);p.serverLevel().getChunk((int)start.x>>4,(int)start.z>>4);p.connection.teleport(start.x,start.y,start.z,-90,-10);p.setDeltaMovement(Vec3.ZERO);
            });return;
        }
        if(!FlatClient.active())return;var d=FlatClient.surface;
        if(stage==1&&ticks-at>280){
            var block=BlockPos.containing(d.storage(new Position(Face.RIGHT,24.5,512.5),76));
            if(!m.level.getBlockState(block).is(Blocks.GOLD_BLOCK)||PortalRenderer.remoteDraws<1||!PortalRenderer.drawnFaces.contains(Face.RIGHT))failure="Destination construction not available in live portal renderer";
            FlatSmokeChecks.requestShot("portal-building-before.png");previousMesh=sourceMesh(block.above(5));
            var server=m.getSingleplayerServer();var id=m.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);var definition=FlatMotion.generator(p.serverLevel()).surface();for(int x=20;x<28;x++)for(int z=508;z<516;z++)for(int y=79;y<83;y++)p.serverLevel().setBlock(BlockPos.containing(definition.storage(new Position(Face.RIGHT,x+.5,z+.5),y)),Blocks.DIAMOND_BLOCK.defaultBlockState(),3);});stage=2;at=ticks;
        }
        if(stage==2&&ticks-at>140){
            var block=BlockPos.containing(d.storage(new Position(Face.RIGHT,24.5,512.5),81));var mesh=sourceMesh(block);
            if(!m.level.getBlockState(block).is(Blocks.DIAMOND_BLOCK)||previousMesh==null||mesh==null||mesh==previousMesh||d.locate(m.player.getX(),m.player.getZ()).face()!=Face.FRONT)failure="Remote live building update failed";
            FlatSmokeChecks.requestShot("portal-building-updated.png");m.options.keyUp.setDown(true);m.options.keySprint.setDown(true);stage=3;at=ticks;
        }
        if(stage==3&&d.locate(m.player.getX(),m.player.getZ()).face()==Face.RIGHT){
            m.options.keyUp.setDown(false);m.options.keySprint.setDown(false);stage=4;at=ticks;
        }
        if(stage==4&&ticks-at>40){FlatSmokeChecks.requestShot("portal-building-after.png");command("planet view");stage=5;at=ticks;}
        if(stage==5&&ticks-at>140){
            if(OrbitBuildRenderer.drawn==0||OrbitBuildRenderer.visibleBlocks<100)failure="Orbital construction LOD not rendered";
            FlatSmokeChecks.requestShot("portal-building-orbit.png");command("planet");stage=6;at=ticks;
        }
        if(stage==6&&ticks-at>160){
            if(!d.relief()||d.faceSize()!=1024||m.player.isNoGravity())failure="Relief planet command/gravity failed";
            FlatSmokeChecks.requestShot("portal-relief-ground.png");command("planet view");stage=7;at=ticks;
        }
        if(stage==7&&ticks-at>100){FlatSmokeChecks.requestShot("portal-relief-orbit.png");finish(failure==null?"PASS: live remote gold building before crossing, remote diamond update before crossing, actual edge crossing, orbital edit LOD, large relief planet, stencil passes="+PortalRenderer.stencilPasses+" remoteDraws="+PortalRenderer.remoteDraws:"FAIL: "+failure);stage=8;at=ticks;}
    }
    private static Object sourceMesh(BlockPos position){try{var field=PortalRenderer.class.getDeclaredField("CACHE");field.setAccessible(true);var map=(java.util.Map<?,?>)field.get(null);return map.get(new BlockPos(position.getX()>>4<<4,position.getY()>>4<<4,position.getZ()>>4<<4));}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static void command(String text){var m=Minecraft.getInstance();var s=m.getSingleplayerServer();var id=m.player.getUUID();s.execute(()->{var p=s.getPlayerList().getPlayer(id);s.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),text);});}
    private static void finish(String text){try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("portal-client-result.txt"),text+"\n");SpaceBlocks.LOGGER.info("PORTAL_RESULT: {}",text);}catch(Exception e){throw new RuntimeException(e);}}
}
