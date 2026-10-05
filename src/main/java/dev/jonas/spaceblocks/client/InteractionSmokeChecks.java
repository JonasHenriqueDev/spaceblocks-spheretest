package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.physics.RadialMotion;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real block interaction packets and persistence checks, enabled only by development JVM flags. */
public final class InteractionSmokeChecks {
    private static int ticks,stage,stageAt;
    private static BlockPos mined;
    private static Direction minedFace;
    private static volatile String failure;
    private static volatile boolean checked;
    private InteractionSmokeChecks() {}

    public static void tick(ClientTickEvent.Post event) {
        boolean interact=Boolean.getBoolean("spaceblocks.interactionSmoke");
        boolean resume=Boolean.getBoolean("spaceblocks.resumeSmoke");
        if(!interact && !resume) return;
        Minecraft minecraft=Minecraft.getInstance();
        ticks++;
        minecraft.options.pauseOnLostFocus=false;
        if(minecraft.screen instanceof PauseScreen) minecraft.setScreen(null);
        if(ticks>800) { result(resume,"FAIL: test timed out");minecraft.stop();return; }
        if(minecraft.player==null || minecraft.level==null) return;
        if(!minecraft.player.isAlive()) { minecraft.player.respawn();minecraft.setScreen(null);return; }
        var player=minecraft.player;
        var server=minecraft.getSingleplayerServer();
        var id=player.getUUID();
        if(resume) {
            if(ticks>100 && stage==0) {
                var session=RadialMotion.session(player);
                boolean radial=RadialMotion.active(player);
                double eyeOffset=session==null?0:player.getEyePosition().subtract(player.position()).dot(session.state.frame().up());
                if(!radial || session==null || !session.state.grounded() || player.getPose()!=Pose.STANDING
                        || session.state.frame().up().y>-.95 || eyeOffset<1.5) {
                    result(true,"FAIL: saved underside state not restored; radial="+radial+" pose="+player.getPose()+" eyeOffset="+eyeOffset);
                    minecraft.stop();return;
                }
                BlockPos savedBlock=new BlockPos(0,34,1);
                try {
                    var saved=Files.readString(minecraft.gameDirectory.toPath().resolve("radial-mined-block.txt")).trim().split(",");
                    savedBlock=new BlockPos(Integer.parseInt(saved[0]),Integer.parseInt(saved[1]),Integer.parseInt(saved[2]));
                } catch(Exception ignored) { /* Older smoke worlds used the original radius-64 coordinate. */ }
                if(!minecraft.level.getBlockState(savedBlock).is(Blocks.STONE)) {
                    result(true,"FAIL: placed test block was not preserved in saved chunks");minecraft.stop();return;
                }
                player.setXRot(0);
                Screenshot.grab(minecraft.gameDirectory,"radial-resume.png",minecraft.getMainRenderTarget(),message->{});
                server.execute(()->{
                    var remote=server.getPlayerList().getPlayer(id);
                    server.getCommands().performPrefixedCommand(remote.createCommandSourceStack().withPermission(4),"space leave");
                    if(SpaceBlocks.isSpace(remote.level().dimension())) failure="Could not return after saved-world resume";
                    checked=true;
                });
                stage=1;stageAt=ticks;
            }
            if(stage==1 && checked && ticks-stageAt>30) {
                result(true,failure==null?"PASS: rejoined on underside; standing capsule, radial eyes, gravity, placed block persistence and original return checkpoint restored.":"FAIL: "+failure);
                minecraft.stop();
            }
            return;
        }
        if(stage==0 && ticks>50) {
            server.execute(()->{
                var remote=server.getPlayerList().getPlayer(id);
                server.getCommands().performPrefixedCommand(remote.createCommandSourceStack().withPermission(4),Boolean.getBoolean("spaceblocks.smallSmoke")?"space legacy":"space radial");
                var body=RadialMotion.session(remote).body;
                remote.teleportTo(remote.serverLevel(),body.centerX()+.5,body.centerY()-body.radius()-12,body.centerZ()+.5,java.util.Set.of(),0,70);
                RadialMotion.reset(remote,false);
                remote.getInventory().setItem(0,new ItemStack(Blocks.STONE,64));
                remote.inventoryMenu.broadcastChanges();
            });
            stage=1;stageAt=ticks;
        }
        if(stage==1 && ticks-stageAt>80 && RadialMotion.active(player) && RadialMotion.session(player).state.grounded()) {
            player.setXRot(55);player.setYRot(0);
            if(!(player.pick(4.5,1,false) instanceof BlockHitResult hit) || hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS) {
                result(false,"FAIL: radial raycast missed underside ground");minecraft.stop();return;
            }
            mined=hit.getBlockPos().immutable();minedFace=hit.getDirection();
            try { Files.writeString(minecraft.gameDirectory.toPath().resolve("radial-mined-block.txt"),mined.getX()+","+mined.getY()+","+mined.getZ()); }
            catch(Exception exception) { failure="Cannot save interaction checkpoint"; }
            SpaceBlocks.LOGGER.info("RADIAL_INTERACTION_MINE: feet={} eye={} pose={} look={} target={} face={}",
                    player.position(),player.getEyePosition(),player.getPose(),player.getLookAngle(),mined,minedFace);
            minecraft.gameMode.startDestroyBlock(mined,hit.getDirection());
            stage=2;stageAt=ticks;
        }
        if(stage==2 && ticks-stageAt>25) {
            server.execute(()->{
                var level=server.getPlayerList().getPlayer(id).serverLevel();
                if(!level.getBlockState(mined).isAir()) failure="Server did not accept underside mining packet at "+mined;
                checked=true;
            });
            stage=3;stageAt=ticks;
        }
        if(stage==3 && checked && ticks-stageAt>10) {
            if(failure!=null) { result(false,"FAIL: "+failure);minecraft.stop();return; }
            BlockPos support=mined.relative(minedFace.getOpposite());
            Vec3 point=Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(minedFace.getNormal()).scale(.5));
            var hit=new BlockHitResult(point,minedFace,support,false);
            var used=minecraft.gameMode.useItemOn(player,InteractionHand.MAIN_HAND,hit);
            SpaceBlocks.LOGGER.info("RADIAL_INTERACTION_PLACE: support={} target={} held={} result={}",support,mined,player.getMainHandItem(),used);
            checked=false;stage=4;stageAt=ticks;
        }
        if(stage==4 && ticks-stageAt>25) {
            server.execute(()->{
                if(!server.getPlayerList().getPlayer(id).serverLevel().getBlockState(mined).is(Blocks.STONE)) failure="Server did not accept stone placement into "+mined;
                checked=true;
            });
            stage=5;stageAt=ticks;
        }
        if(stage==5 && checked && ticks-stageAt>10) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);player.setXRot(0);
            stage=6;stageAt=ticks;
        }
        if(stage==6 && ticks-stageAt>10) {
            Screenshot.grab(minecraft.gameDirectory,"radial-standing-underside.png",minecraft.getMainRenderTarget(),message->{});
            result(false,failure==null?"PASS: radial underside raycast, real creative mining packet and stone placement accepted by server. Saved on underside for resume test.":"FAIL: "+failure);
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.stop();
        }
    }

    private static void result(boolean resume,String message) {
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve(resume?"radial-resume-result.txt":"radial-interaction-result.txt"),message+"\n");
            SpaceBlocks.LOGGER.info("RADIAL_{}_RESULT: {}",resume?"RESUME":"INTERACTION",message);
        } catch(Exception exception) { SpaceBlocks.LOGGER.error("Cannot save test result",exception); }
    }
}
