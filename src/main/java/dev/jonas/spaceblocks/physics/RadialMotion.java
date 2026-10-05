package dev.jonas.spaceblocks.physics;

import dev.jonas.spaceblocks.PlanetChunkGenerator;
import dev.jonas.spaceblocks.PlanetDefinition;
import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.network.MotionPayload;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RadialMotion {
    public static final String FLIGHT_KEY = "spaceblocks_free_flight";
    private static final AtomicInteger GENERATION = new AtomicInteger();
    // Entity equality uses numeric IDs, which are shared by integrated client and server.
    private static final Map<Entity, Session> SERVER = new WeakHashMap<>();
    private static final Map<Entity, Session> CLIENT = new WeakHashMap<>();

    public static final class Session {
        public final int generation;
        public final PlanetDefinition body;
        public RadialPhysics.State state;
        public GravityFrame previousFrame;
        public boolean flight;
        public final ArrayDeque<RadialPhysics.Input> inputs = new ArrayDeque<>();
        public RadialPhysics.Input lastInput = RadialPhysics.Input.IDLE;
        public int received = -1;
        public int processed = -1;
        public int idleTicks;

        public Session(int generation, PlanetDefinition body, RadialPhysics.State state, boolean flight) {
            this.generation = generation;
            this.body = body;
            this.state = state;
            this.previousFrame = state.frame();
            this.flight = flight;
        }
    }

    private RadialMotion() {}
    public static Session session(Entity entity) { return (entity.level().isClientSide ? CLIENT : SERVER).get(entity); }
    public static void clientSession(Entity entity, Session session) { CLIENT.put(entity, session); }
    public static void forget(Entity entity) { (entity.level().isClientSide ? CLIENT : SERVER).remove(entity); }
    public static boolean active(Entity entity) {
        if (!(entity instanceof Player player) || !SpaceBlocks.isSpace(player.level().dimension())) return false;
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.gameMode == null) return false;
        if (player.isSpectator() || !player.isAlive()) return false;
        if (!player.level().isClientSide) return !player.getPersistentData().getBoolean(FLIGHT_KEY);
        Session session = session(player);
        return session != null && !session.flight;
    }

    public static void reset(ServerPlayer player, boolean flight) {
        player.getPersistentData().putBoolean(FLIGHT_KEY, flight);
        PlanetDefinition body = ((PlanetChunkGenerator) player.serverLevel().getChunkSource().getGenerator()).planet();
        var state = RadialPhysics.State.at(player.position(), body);
        Vec3 north = new Vec3(player.getPersistentData().getDouble("spaceblocks_north_x"),
                player.getPersistentData().getDouble("spaceblocks_north_y"),player.getPersistentData().getDouble("spaceblocks_north_z"));
        if (north.lengthSqr() > 0.5) {
            Vec3 tangent = GravityFrame.tangent(north, state.frame().up());
            if (tangent.lengthSqr() > 0.1) state = new RadialPhysics.State(state.position(),state.velocity(),
                    new GravityFrame(state.frame().up(),tangent.normalize()),false,false);
        }
        Session session = new Session(GENERATION.incrementAndGet(), body, state, flight);
        SERVER.put(player, session);
        applyAbilities(player, flight);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, MotionPayload.of(player,session));
    }

    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player && SpaceBlocks.isSpace(player.level().dimension())
                && player.serverLevel().getChunkSource().getGenerator() instanceof PlanetChunkGenerator)
            reset(player,player.getPersistentData().getBoolean(FLIGHT_KEY));
    }

    public static void applyAbilities(Player player, boolean flight) {
        boolean changed = player.getAbilities().mayfly != flight || player.getAbilities().flying != flight;
        player.getAbilities().mayfly = flight;
        player.getAbilities().flying = flight;
        player.setNoGravity(true); // The radial controller supplies gravity instead of vanilla -Y.
        if (changed && player instanceof ServerPlayer serverPlayer) serverPlayer.onUpdateAbilities();
    }

    public static Vec3 eye(Entity entity, float partial) {
        Session session = session(entity);
        Vec3 position = entity.getPosition(partial);
        Vec3 up = RadialPhysics.up(position, session.body);
        return position.add(up.scale(entity.getEyeHeight()));
    }
    public static GravityFrame frame(Entity entity, float partial) {
        Session session = session(entity);
        return session.state.frame().transport(RadialPhysics.up(entity.getPosition(partial),session.body));
    }

    public static void apply(Player player, Session session) {
        RadialPhysics.State state = session.state;
        player.setPos(state.position());
        player.setDeltaMovement(state.velocity());
        player.setBoundingBox(RadialPhysics.bounds(state.position(),state.frame().up(),player.isShiftKeyDown()?1.5:1.8));
        player.setOnGround(state.grounded());
        player.verticalCollisionBelow = state.grounded();
        player.fallDistance = 0;
        player.walkAnimation.update((float)GravityFrame.tangent(state.velocity(),state.frame().up()).length()*4,0.4f);
    }

    public static void serverTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!SpaceBlocks.isSpace(player.level().dimension())) { forget(player); continue; }
            if (!(player.serverLevel().getChunkSource().getGenerator() instanceof PlanetChunkGenerator)) continue;
            Session session = session(player);
            if (session == null) { reset(player,player.getPersistentData().getBoolean(FLIGHT_KEY)); session=session(player); }
            if (player.isSpectator() || !player.isAlive()) continue;
            if (session.flight) {
                session.state = RadialPhysics.State.at(player.position(),session.body);
                applyAbilities(player,true);
            } else {
                applyAbilities(player,false);
                // Inputs describe current key state, not additional simulation time.
                // Coalesce a burst after chunk generation so movement does not stay seconds behind.
                RadialPhysics.Input input=null, queued;
                boolean jumpPressed=false;
                while((queued=session.inputs.pollFirst())!=null) {
                    input=queued;
                    jumpPressed |= queued.jump();
                }
                if(input != null) {
                    if(jumpPressed && !input.jump()) input=new RadialPhysics.Input(input.sequence(),input.forward(),input.left(),
                            true,input.sprint(),input.crouch(),input.yaw(),input.pitch());
                    session.lastInput=input;
                    session.processed=input.sequence();
                    session.idleTicks=0;
                } else {
                    session.idleTicks++;
                    input=session.idleTicks>5 ? new RadialPhysics.Input(session.processed,0,0,false,false,false,player.getYRot(),player.getXRot()) : session.lastInput;
                }
                player.setYRot(input.yaw());
                player.setXRot(input.pitch());
                player.setYHeadRot(input.yaw());
                player.setShiftKeyDown(input.crouch());
                player.setSprinting(input.sprint());
                session.previousFrame=session.state.frame();
                session.state=RadialPhysics.tick(session.state,input,session.body,new WorldCollider(player.level(),player));
                apply(player,session);
                player.serverLevel().getChunkSource().move(player);
                player.connection.resetPosition();
                Vec3 north=session.state.frame().north();
                player.getPersistentData().putDouble("spaceblocks_north_x",north.x);
                player.getPersistentData().putDouble("spaceblocks_north_y",north.y);
                player.getPersistentData().putDouble("spaceblocks_north_z",north.z);
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,MotionPayload.of(player,session));
        }
    }
}
