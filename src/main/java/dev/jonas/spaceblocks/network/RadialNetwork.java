package dev.jonas.spaceblocks.network;

import dev.jonas.spaceblocks.client.ClientRadialMotion;
import dev.jonas.spaceblocks.physics.RadialMotion;
import dev.jonas.spaceblocks.physics.RadialPhysics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class RadialNetwork {
    private RadialNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("3");
        registrar.playToServer(InputPayload.TYPE,InputPayload.CODEC,RadialNetwork::input);
        registrar.playToClient(MotionPayload.TYPE,MotionPayload.CODEC,(payload,context)->ClientRadialMotion.receive(payload));
    }
    private static void input(InputPayload payload,IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !RadialMotion.active(player)) return;
        var session=RadialMotion.session(player);
        var input=payload.input();
        if(session==null || session.generation!=payload.generation()
                || input.sequence()<=session.received || input.sequence()>session.received+100) return;
        if(!Float.isFinite(input.forward()) || !Float.isFinite(input.left())
                || !Float.isFinite(input.yaw()) || !Float.isFinite(input.pitch())) return;
        session.received=input.sequence();
        if(session.inputs.size()>=40) session.inputs.removeFirst();
        session.inputs.addLast(new RadialPhysics.Input(input.sequence(),Mth.clamp(input.forward(),-1,1),
                Mth.clamp(input.left(),-1,1),input.jump(),input.sprint(),input.crouch(),
                Mth.wrapDegrees(input.yaw()),Mth.clamp(input.pitch(),-90,90)));
    }
}
