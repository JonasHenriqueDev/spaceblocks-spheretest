package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.network.InputPayload;
import dev.jonas.spaceblocks.network.MotionPayload;
import dev.jonas.spaceblocks.physics.RadialMotion;
import dev.jonas.spaceblocks.physics.RadialPhysics;
import dev.jonas.spaceblocks.physics.WorldCollider;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientRadialMotion {
    private static final ArrayDeque<RadialPhysics.Input> PENDING=new ArrayDeque<>();
    private static int sequence;
    private static int generation=-1;
    private ClientRadialMotion() {}

    public static void receive(MotionPayload payload) {
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null || !SpaceBlocks.isSpace(minecraft.level.dimension())) return;
        var entity=minecraft.level.getEntity(payload.entityId());
        if(entity==null) return;
        var old=RadialMotion.session(entity);
        var session=new RadialMotion.Session(payload.generation(),payload.body(),payload.state(),payload.flight());
        if(old!=null) session.previousFrame=old.state.frame();
        RadialMotion.clientSession(entity,session);
        if(entity!=minecraft.player) return;
        if(generation!=payload.generation()) {
            generation=payload.generation();sequence=0;PENDING.clear();
        }
        if(payload.flight()) { PENDING.clear(); return; }
        while(!PENDING.isEmpty() && PENDING.peekFirst().sequence()<=payload.acknowledged()) PENDING.removeFirst();
        var collider=new WorldCollider(minecraft.level,minecraft.player);
        for(var input:PENDING) session.state=RadialPhysics.tick(session.state,input,session.body,collider);
        RadialMotion.apply(minecraft.player,session);
        RadialMotion.applyAbilities(minecraft.player,false);
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.player==null || minecraft.level==null || !RadialMotion.active(minecraft.player)) {
            if(minecraft.level==null || !SpaceBlocks.isSpace(minecraft.level.dimension())) { PENDING.clear();generation=-1; }
            return;
        }
        if(minecraft.isPaused()) return;
        var player=minecraft.player;
        var session=RadialMotion.session(player);
        RadialMotion.applyAbilities(player,false);
        boolean movementEnabled=minecraft.screen==null;
        var input=new RadialPhysics.Input(sequence++,movementEnabled?player.input.forwardImpulse:0,
                movementEnabled?player.input.leftImpulse:0,movementEnabled&&player.input.jumping,
                movementEnabled&&minecraft.options.keySprint.isDown(),movementEnabled&&player.input.shiftKeyDown,
                player.getYRot(),player.getXRot());
        var automatic=RadialWalkSmoke.input(player,session,input);
        if(automatic!=null) input=automatic;
        PENDING.addLast(input);
        if(PENDING.size()>80) PENDING.removeFirst();
        PacketDistributor.sendToServer(new InputPayload(session.generation,input));
        session.previousFrame=session.state.frame();
        session.state=RadialPhysics.tick(session.state,input,session.body,new WorldCollider(minecraft.level,player));
        RadialMotion.apply(player,session);
    }
}
