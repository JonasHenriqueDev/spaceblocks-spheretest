package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.physics.RadialMotion;
import dev.jonas.spaceblocks.physics.RadialPhysics;
import dev.jonas.spaceblocks.SpaceBlocks;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Drives real network inputs for a complete in-game circuit and an underside jump. */
public final class RadialWalkSmoke {
    private static int strafeTicks;
    private static Vec3 strafeStart, strafeLeft;
    private static int ticks;
    private static int allTicks;
    private static int walkingTicks;
    private static int groundedTicks;
    private static boolean entered,started,equatorShot,bottomShot,finalShot,requestedCheck;
    private static double angle,lastAngle,jumpStartRadius,jumpRise;
    private static int jumpStage,jumpTicks;
    private static Vec3 initial;
    private static volatile boolean serverChecked;
    private static volatile String failure;
    private static int leaveAt,viewAt,obliqueAt,finalAt;
    private static boolean obliqueShot;
    private RadialWalkSmoke() {}
    public static RadialPhysics.Input input(LocalPlayer player, RadialMotion.Session session,RadialPhysics.Input normal) {
        if(!Boolean.getBoolean("spaceblocks.radialSmoke") || !entered || finalShot) return null;
        Vec3 radial=player.position().subtract(session.body.centerX(),session.body.centerY(),session.body.centerZ());
        if(strafeTicks<45 && session.state.grounded()) {
            var options=Minecraft.getInstance().options;
            if(strafeTicks==0) { strafeStart=player.position();strafeLeft=session.state.frame().left(0); }
            options.keyLeft.setDown(strafeTicks<21);
            options.keyRight.setDown(strafeTicks>=23 && strafeTicks<44);
            player.setYRot(0);player.setXRot(0);
            if(strafeTicks==22) {
                double moved=player.position().subtract(strafeStart).dot(strafeLeft);
                if(moved<2) failure="A key moved in wrong direction: "+moved;
                SpaceBlocks.LOGGER.info("RADIAL_STRAFE_A: camera-left displacement={}",moved);
                strafeStart=player.position();
            }
            if(strafeTicks==44) {
                double moved=player.position().subtract(strafeStart).dot(strafeLeft);
                if(moved> -2) failure="D key moved in wrong direction: "+moved;
                SpaceBlocks.LOGGER.info("RADIAL_STRAFE_D: camera-left displacement={}",moved);
            }
            strafeTicks++;
            return new RadialPhysics.Input(normal.sequence(),0,normal.left(),false,false,false,0,0);
        }
        double current=Math.atan2(radial.z,radial.y);
        if(!started) {
            if(!session.state.grounded()) return new RadialPhysics.Input(normal.sequence(),0,0,false,false,false,0,0);
            initial=player.position();lastAngle=current;started=true;
            SpaceBlocks.LOGGER.info("RADIAL_SMOKE_WALK_STARTED: {}",initial);
        }
        double delta=current-lastAngle;
        if(delta>Math.PI) delta-=Math.PI*2;
        if(delta<-Math.PI) delta+=Math.PI*2;
        angle+=delta;lastAngle=current;
        walkingTicks++;
        if(player.getPose()!=Pose.STANDING && walkingTicks>10) failure="Unexpected player pose while walking: "+player.getPose();
        if(session.state.grounded()) groundedTicks++;
        if(walkingTicks%200==0) SpaceBlocks.LOGGER.info("RADIAL_SMOKE_PROGRESS: degrees={} position={} grounded={}",Math.toDegrees(angle),player.position(),session.state.grounded());
        Vec3 forward=new Vec3(1,0,0).cross(session.state.frame().up()).normalize();
        float desired=(float)Math.toDegrees(Math.atan2(-forward.dot(session.state.frame().east()),forward.dot(session.state.frame().north())));
        float yaw=player.getYRot()+Mth.wrapDegrees(desired-player.getYRot());
        player.setYRot(yaw);player.setXRot(0);
        if(angle>=Math.PI && jumpStage==0 && session.state.grounded()) {
            jumpStage=1;jumpTicks=0;jumpStartRadius=radial.length();
        }
        if(jumpStage==1) {
            jumpRise=Math.max(jumpRise,radial.length()-jumpStartRadius);
            boolean jump=jumpTicks++==0;
            if(jumpTicks>15 && session.state.grounded()) {
                if(jumpRise<0.7) failure="Underside jump too small: "+jumpRise;
                jumpStage=2;
                SpaceBlocks.LOGGER.info("RADIAL_SMOKE_UNDERSIDE_JUMP: rise={}",jumpRise);
            } else return new RadialPhysics.Input(normal.sequence(),0,0,jump,false,false,yaw,0);
        }
        return new RadialPhysics.Input(normal.sequence(),angle>=Math.PI*2?0:1,0,false,true,false,yaw,0);
    }

    public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("spaceblocks.radialSmoke")) return;
        Minecraft minecraft=Minecraft.getInstance();
        if(++allTicks>900 && minecraft.player==null) { saveResult("FAIL: client could not join the test world");minecraft.stop();return; }
        minecraft.options.pauseOnLostFocus=false;
        if(minecraft.screen instanceof PauseScreen) minecraft.setScreen(null);
        if(minecraft.player==null || minecraft.level==null) return;
        ticks++;
        if(!entered && ticks>40) { command(Boolean.getBoolean("spaceblocks.smallSmoke")?"space legacy":"space radial");entered=true; }
        if(started && angle>=Math.PI/4 && !obliqueShot) {
            if(obliqueAt==0) { minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);obliqueAt=ticks+4; }
            if(ticks>=obliqueAt) { shot("radial-large-oblique.png");obliqueShot=true;minecraft.options.setCameraType(CameraType.FIRST_PERSON); }
        }
        if(started && walkingTicks>200 && Math.abs(Mth.wrapDegrees(minecraft.player.yBodyRot-minecraft.player.getYRot()))>10)
            failure="Body orientation diverged from local heading";
        if(started && angle>=Math.PI/2 && !equatorShot) { shot("radial-equator.png");equatorShot=true; }
        if(jumpStage==1 && jumpTicks==2) minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if(jumpStage==1 && jumpTicks>=6 && !bottomShot) { shot("radial-underside.png");bottomShot=true; }
        if(jumpStage==2) minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        if(started && angle>=Math.PI*2 && !finalShot) {
            shot("radial-full-circuit.png");finalShot=true;finalAt=ticks;
            SpaceBlocks.LOGGER.info("RADIAL_SMOKE_CLIENT_CIRCUIT: ticks={} degrees={} groundFraction={} returnDistance={}",
                    walkingTicks,Math.toDegrees(angle),(double)groundedTicks/walkingTicks,minecraft.player.position().distanceTo(initial));
        }
        if(finalShot && ticks-finalAt>80 && !requestedCheck) {
            requestedCheck=true;
            var server=minecraft.getSingleplayerServer();
            var id=minecraft.player.getUUID();
            Vec3 clientPosition=minecraft.player.position();
            server.execute(()->{
                var player=server.getPlayerList().getPlayer(id);
                var session=RadialMotion.session(player);
                double discrepancy=player.position().distanceTo(clientPosition);
                if(session==null || session.processed<900 || discrepancy>2 || !RadialMotion.active(player))
                    failure="Server validation: processed="+(session==null?-1:session.processed)+" discrepancy="+discrepancy;
                SpaceBlocks.LOGGER.info("RADIAL_SMOKE_SERVER_CIRCUIT: processed={} discrepancy={}",session==null?-1:session.processed,discrepancy);
                serverChecked=true;
            });
        }
        if(serverChecked && viewAt==0) {
            if((double)groundedTicks/walkingTicks<0.75) failure="Unstable ground contact";
            saveResult(failure==null?"PASS: real A/D keys, full in-game circuit, underside jump, real network input and server position verified. ticks="+walkingTicks+" angle="+angle+" jumpRise="+jumpRise:"FAIL: "+failure);
            command("space view");viewAt=ticks+100;
        }
        if(viewAt>0 && ticks>=viewAt && leaveAt==0) {
            shot("radial-large-distant.png");command("space leave");leaveAt=ticks+40;
        }
        if(leaveAt>0 && ticks>=leaveAt) {
            if(SpaceBlocks.isSpace(minecraft.level.dimension())) saveResult("FAIL: return command did not leave the dimension");
            minecraft.stop();
        }
        if(ticks>10000) { saveResult("FAIL: circuit timed out at angle="+angle);minecraft.stop(); }
    }

    private static void shot(String filename) {
        Minecraft minecraft=Minecraft.getInstance();
        Screenshot.grab(minecraft.gameDirectory,filename,minecraft.getMainRenderTarget(),message->SpaceBlocks.LOGGER.info("RADIAL_SMOKE_SCREENSHOT: {}",message.getString()));
    }
    private static void command(String command) {
        Minecraft minecraft=Minecraft.getInstance();
        var server=minecraft.getSingleplayerServer();var id=minecraft.player.getUUID();
        server.execute(()->{
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null) server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),command);
        });
    }
    private static void saveResult(String result) {
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("radial-client-result.txt"),result+"\n");
            SpaceBlocks.LOGGER.info("RADIAL_SMOKE_RESULT: {}",result);
        } catch(Exception exception) { SpaceBlocks.LOGGER.error("Cannot write smoke result",exception); }
    }
}
