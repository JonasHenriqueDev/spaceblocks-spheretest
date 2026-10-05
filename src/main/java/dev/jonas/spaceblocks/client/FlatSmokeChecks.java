package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.surface.*;
import java.nio.file.Files;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

/** Real vanilla-key movement, all directed seams, a closed circuit and visual altitude snapshots. */
public final class FlatSmokeChecks {
    private static int ticks,stage,stageAt,edgeIndex,moveAt,cycleStart,crossedCount;
    private static Face previous;
    private static Vec3 initial;
    private static boolean prepared,enterRequested;
    private static volatile boolean checked;
    private static volatile String failure;
    private static int cornerIndex,cornerStart;
    private static String pendingShot;
    public static void requestShot(String filename) {pendingShot=filename;}
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if(pendingShot==null)return;String filename=pendingShot;pendingShot=null;
        var m=Minecraft.getInstance();Screenshot.grab(m.gameDirectory,filename,m.getMainRenderTarget(),message->{});
    }
    public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("spaceblocks.flatSmoke"))return;
        var m=Minecraft.getInstance();ticks++;
        m.options.pauseOnLostFocus=false;
        if(m.screen instanceof PauseScreen)m.setScreen(null);
        if(stage==7){if(ticks-stageAt>40)m.stop();return;}
        if(ticks>7000){result("FAIL: timeout stage="+stage+" edge="+edgeIndex+" "+failure);m.stop();return;}
        if(m.player==null||m.level==null||!m.player.isAlive())return;
        if(!enterRequested&&ticks>60){command("space flat_test");enterRequested=true;stageAt=ticks;}
        if(!FlatClient.active())return;
        var d=FlatClient.surface;var player=m.player;var p=d.locate(player.getX(),player.getZ());if(p==null)return;
        if(player.tickCount%100==0)SpaceBlocks.LOGGER.info("FLAT_SMOKE_PROGRESS: stage={} edge={} chart={} feet={} grounded={}",stage,edgeIndex,p.face(),player.position(),player.onGround());
        if(stage==0&&ticks-stageAt>50){stage=Boolean.getBoolean("spaceblocks.cornerOnly")?8:1;stageAt=ticks;}
        if(stage==1) {
            if(edgeIndex>=24){stage=2;prepared=false;stageAt=ticks;return;}
            Face face=Face.values()[edgeIndex/4];Edge edge=Edge.values()[edgeIndex%4];
            if(!prepared) {
                m.options.keyUp.setDown(false);m.options.keySprint.setDown(false);
                double u=edge==Edge.WEST?4.5:edge==Edge.EAST?d.faceSize()-4.5:d.faceSize()*.5+.5;
                double v=edge==Edge.NORTH?4.5:edge==Edge.SOUTH?d.faceSize()-4.5:d.faceSize()*.5+.5;
                float yaw=edge==Edge.WEST?90:edge==Edge.EAST?-90:edge==Edge.NORTH?180:0;
                teleport(new Position(face,u,v),yaw);prepared=true;moveAt=ticks+60;previous=face;
            }
            if(ticks>=moveAt&&p.face()==previous) {m.options.keyUp.setDown(true);m.options.keySprint.setDown(true);}
            if(ticks>=moveAt&&p.face()!=previous) {
                m.options.keyUp.setDown(false);m.options.keySprint.setDown(false);
                if(player.getPose()!=Pose.STANDING)failure="Seam changed player pose: "+player.getPose();
                if(Math.abs(player.getDeltaMovement().y)>1)failure="Vertical impulse at a flat seam";
                SpaceBlocks.LOGGER.info("FLAT_SMOKE_EDGE_OK: {} / {} -> {} snapshots={}",face,edge,p.face(),PortalRenderer.remoteDraws);
                edgeIndex++;prepared=false;
            }
            if(prepared&&ticks>moveAt+150){failure="Could not cross "+face+" / "+edge;result("FAIL: "+failure);m.stop();}
            return;
        }
        if(stage==2) {
            if(!prepared){teleport(new Position(Face.FRONT,8.5,d.faceSize()*.5+.5),-90);prepared=true;moveAt=ticks+60;return;}
            if(ticks==moveAt){initial=player.position();cycleStart=FlatClient.seams;m.options.keyUp.setDown(true);m.options.keySprint.setDown(true);}
            if(ticks>moveAt&&FlatClient.seams-cycleStart>=4&&p.face()==Face.FRONT&&p.u()>=8.5){
                m.options.keyUp.setDown(false);m.options.keySprint.setDown(false);crossedCount=FlatClient.seams-cycleStart;
                shot("flat-full-circuit.png");stage=3;stageAt=ticks;
            }
            if(ticks>moveAt+1800){result("FAIL: circuit did not close");m.stop();}
            return;
        }
        if(stage==3&&ticks-stageAt>60) {
            var server=m.getSingleplayerServer();var id=player.getUUID();Vec3 client=player.position();
            server.execute(()->{
                var remote=server.getPlayerList().getPlayer(id);
                double error=remote.position().distanceTo(client);
                if(error>.6||remote.isNoGravity()||remote.getPose()!=Pose.STANDING)failure="Server continuity/normal gravity failed: error="+error;
                SpaceBlocks.LOGGER.info("FLAT_SMOKE_SERVER: discrepancy={} normalGravity={}",error,!remote.isNoGravity());checked=true;
            });
            stage=4;stageAt=ticks;
        }
        if(stage==4&&checked&&ticks-stageAt>10) {
            if(failure!=null){result("FAIL: "+failure);m.stop();return;}
            shot("flat-ground.png");command("space view");stage=5;stageAt=ticks;
        }
        if(stage==5&&ticks-stageAt>80){shot("flat-orbit.png");stage=6;stageAt=ticks;command("space surface");}
        if(stage==6&&ticks-stageAt>80) {
            shot("flat-return-surface.png");
            if(PortalRenderer.remoteDraws<28||PortalRenderer.stencilPasses<10)failure="Live portal renderer was not exercised";
            stage=8;stageAt=ticks;prepared=false;
        }
        if(stage==8) {
            if(cornerIndex>=8){stage=9;stageAt=ticks;prepared=false;return;}
            Face face=cornerIndex<4?Face.FRONT:Face.BACK;
            int corner=cornerIndex%4;double u=(corner&1)==0?3.5:d.faceSize()-3.5,v=(corner&2)==0?3.5:d.faceSize()-3.5;
            if(!prepared){teleport(new Position(face,u,v),0);prepared=true;moveAt=ticks+60;return;}
            if(ticks==moveAt) {
                var view=FlatVisual.view(player.getEyePosition());var desired=view.jacobian(view.eye()).transform(new org.joml.Vector3d((corner&1)==0?-1:1,0,(corner&2)==0?-1:1));
                player.setYRot((float)Math.toDegrees(Math.atan2(-desired.x,desired.z)));player.setXRot(60);
                cornerStart=FlatClient.seams;m.options.keyUp.setDown(true);m.options.keySprint.setDown(true);
                shot("flat-corner-"+cornerIndex+"-before.png");
                var hit=FlatVisual.pick(player,4.5f,1,false);
                if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)failure="Corner picking failed at "+cornerIndex;
            }
            if(ticks>moveAt&&FlatClient.seams>cornerStart) {
                m.options.keyUp.setDown(false);m.options.keySprint.setDown(false);shot("flat-corner-"+cornerIndex+"-after.png");
                if(Math.abs(player.getY()-65)>.01||player.isNoGravity())failure="Corner changed physical floor/gravity";
                SpaceBlocks.LOGGER.info("FLAT_CORNER_OK: vertex={} crossed={} normalGravity={} selection={}",cornerIndex,FlatClient.seams-cornerStart,!player.isNoGravity(),failure);
                cornerIndex++;prepared=false;
            }
            if(ticks>moveAt+200){failure="Corner walking failed at "+cornerIndex;result("FAIL: "+failure);m.stop();}
            return;
        }
        if(stage==9) {
            if(!prepared) {
                prepared=true;checked=false;var server=m.getSingleplayerServer();var id=player.getUUID();
                server.execute(()->{
                    var remote=server.getPlayerList().getPlayer(id);var level=remote.serverLevel();var g=FlatMotion.generator(level);var definition=g.surface();
                    var core=new Position(Face.FRONT,definition.faceSize()-.5,12.5);
                    for(var alias:FlatMotion.aliases(definition,core)){Vec3 location=definition.storage(alias,64);level.getChunk((int)location.x>>4,(int)location.z>>4);}
                    var block=net.minecraft.core.BlockPos.containing(definition.storage(core,68));
                    level.setBlock(block,net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.EAST),3);
                    var low=net.minecraft.core.BlockPos.containing(definition.storage(core,12));level.setBlock(low,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                });stageAt=ticks;return;
            }
            if(!checked&&ticks-stageAt>20) {
                checked=true;var server=m.getSingleplayerServer();var id=player.getUUID();
                server.execute(()->{
                    var level=server.getPlayerList().getPlayer(id).serverLevel();var g=FlatMotion.generator(level);var definition=g.surface();var core=new Position(Face.FRONT,definition.faceSize()-.5,12.5);
                    var expected=net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.EAST);
                    int count=0;
                    for(var alias:FlatMotion.aliases(definition,core)) {
                        var block=net.minecraft.core.BlockPos.containing(definition.storage(alias,68));
                        if(!level.getBlockState(block).equals(expected.rotate(net.minecraft.world.level.block.Rotation.values()[Math.floorMod(-definition.quarterTurns(alias),4)])))failure="Directional block copy failed: "+alias;
                        count++;
                    }
                    for(var alias:FlatMotion.aliases(definition,core)) {
                        Vec3 storage=definition.storage(alias,12);
                        if(!g.blockAt((int)Math.floor(storage.x),12,(int)Math.floor(storage.z)).is(net.minecraft.world.level.block.Blocks.STONE))failure="Regenerated low construction differs at "+alias;
                    }
                    if(g.edits.lowestY()>12||g.edits.get(definition.address(core,12))==null)failure="Persistent construction below terrain failed";
                    SpaceBlocks.LOGGER.info("FLAT_BUILDING_OK: copies={} savedEdits={} lowestY={}",count,g.edits.isDirty(),g.edits.lowestY());
                });
            }
            if(ticks-stageAt>40){command("space enter");stage=10;stageAt=ticks;return;}
        }
        if(stage==10&&ticks-stageAt>160){shot("flat-large-ground.png");command("space view");stage=11;stageAt=ticks;}
        if(stage==11&&ticks-stageAt>120){shot("flat-large-orbit.png");command("space surface");stage=12;stageAt=ticks;}
        if(stage==12&&ticks-stageAt>80) {
            shot("flat-large-return.png");
            String coverage=Boolean.getBoolean("spaceblocks.cornerOnly")?"8 corrected corners and picking, shared directional blocks, saved edits, relief planet orbit/return":"24 directed edges, 4-face closed circuit, 8 corrected corners and picking, normal gravity, server position, shared directional blocks, saved edits, relief planet orbit/return";
            result(failure==null?"PASS: "+coverage+"; live source-chunk portal rendering. remoteDraws="+PortalRenderer.remoteDraws+" stencilPasses="+PortalRenderer.stencilPasses:"FAIL: "+failure);
            command("space leave");stage=7;stageAt=ticks;
        }
        if(stage==7&&ticks-stageAt>40)m.stop();
    }
    private static void teleport(Position p,float yaw) {
        var m=Minecraft.getInstance();var server=m.getSingleplayerServer();var id=m.player.getUUID();
        server.execute(()->{
            var player=server.getPlayerList().getPlayer(id);var d=FlatMotion.generator(player.serverLevel()).surface();Vec3 point=d.storage(p,d.height(p)+1);
            player.serverLevel().getChunk((int)point.x>>4,(int)point.z>>4);
            player.connection.teleport(point.x,point.y,point.z,yaw,0);player.setDeltaMovement(Vec3.ZERO);
        });
    }
    private static void command(String text) {
        var m=Minecraft.getInstance();var server=m.getSingleplayerServer();var id=m.player.getUUID();
        server.execute(()->{var p=server.getPlayerList().getPlayer(id);server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),text);});
    }
    private static void shot(String filename){pendingShot=filename;}
    private static void result(String text){try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("flat-client-result.txt"),text+"\n");SpaceBlocks.LOGGER.info("FLAT_SMOKE_RESULT: {}",text);}catch(Exception e){throw new RuntimeException(e);}}
}
