package dev.jonas.spaceblocks;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Periodic streaming owns canonical tickets and only sends unique source chunks. */
public final class PlanetServer {
  public static final TicketType<ChunkPos> PERIODIC =
      TicketType.create("spaceblocks_periodic", Comparator.comparingLong(ChunkPos::toLong));
  private static final Map<ServerPlayer, Set<Long>> SENT = new WeakHashMap<>();
  private static final Map<ServerLevel, Set<Long>> TICKETS = new WeakHashMap<>();
  private static final Map<ServerLevel, Set<BlockPos>> CHANGED = new WeakHashMap<>();
  private static final Map<ServerPlayer, ViewLease> BOTTOM_VIEW = new WeakHashMap<>();

  private record ViewLease(int expires, boolean projection) {}

  public static void bottomView(ServerPlayer player, boolean visible, boolean projection) {
    int range =
        Math.min(player.requestedViewDistance(), player.server.getPlayerList().getViewDistance());
    if (!visible
        || !BottomView.looking(
            player.level(),
            player,
            player.getEyePosition(),
            player.getViewVector(1),
            range,
            projection)) {
      BOTTOM_VIEW.remove(player);
      return;
    }
    BOTTOM_VIEW.put(player, new ViewLease(player.server.getTickCount() + 30, projection));
  }

  /** Visual area only while looking; a 3x3 safety area immediately before physical passage. */
  public static int bottomRange(ServerPlayer player) {
    var p = Planet.of(player.level());
    if (p == null || !PlanetSettings.get(player.level()).fallthrough) return -1;
    if (BOTTOM_VIEW.containsKey(player)
        && BOTTOM_VIEW.get(player).expires >= player.server.getTickCount())
      return Math.min(
          Math.min(player.requestedViewDistance(), player.server.getPlayerList().getViewDistance()),
          p.size() / 32);
    return Math.abs(player.getY() - p.bottom()) <= 4 ? 1 : -1;
  }

  public static void reset(ServerPlayer player) {
    SENT.remove(player);
    BOTTOM_VIEW.remove(player);
  }

  public static void dimensionChanged(
      net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      reset(player);
      if (Planet.of(player.level()) != null) PlanetNetwork.sync(player);
    }
  }

  public static void stopped(ServerStoppedEvent e) {
    SENT.clear();
    TICKETS.clear();
    CHANGED.clear();
    BOTTOM_VIEW.clear();
  }

  public static void changed(ServerLevel level, BlockPos p) {
    if (Planet.of(level) != null)
      CHANGED.computeIfAbsent(level, k -> new HashSet<>()).add(p.immutable());
  }

  public static boolean tracked(ServerPlayer player, int cx, int cz) {
    var d = Planet.of(player.level());
    if (d == null) return false;
    int r =
        Math.min(player.requestedViewDistance(), player.server.getPlayerList().getViewDistance());
    int dx = PeriodicMath.wrap(cx - player.chunkPosition().x, d.size() / 16),
        dz = PeriodicMath.wrap(cz - player.chunkPosition().z, d.size() / 16);
    if (bottomRange(player) >= 0)
      dx =
          Math.abs(dx) < Math.abs(PeriodicMath.wrap(dx + d.size() / 32, d.size() / 16))
              ? dx
              : PeriodicMath.wrap(dx + d.size() / 32, d.size() / 16);
    return Math.abs(dx) <= r
        && Math.abs(dz) <= r
        && SENT.getOrDefault(player, Set.of()).contains(ChunkPos.asLong(d.chunk(cx), d.chunk(cz)));
  }

  public static void wrap(Entity e) {
    if (e == null) return;
    var d = Planet.of(e.level());
    if (d == null || e.isRemoved() || !e.isAlive() || e.isPassenger()) return;
    double x = PeriodicMath.wrap(e.getX(), d.size()),
        z = PeriodicMath.wrap(e.getZ(), d.size()),
        y = e.getY();
    Vec3 velocity = e.getDeltaMovement();
    boolean bounce = PlanetSettings.get(e.level()).fallthrough && y < d.bottom();
    if (bounce) {
      double destinationX = PeriodicMath.wrap(x + d.size() / 2.0, d.size());
      double destinationY = d.bottom() + 1;
      if (e instanceof ServerPlayer player) {
        player
            .serverLevel()
            .getChunk((int) Math.floor(destinationX) >> 4, (int) Math.floor(z) >> 4);
      }
      // Do not place a player inside the still-solid exit. The local bottom view lets them mine it.
      boolean clear =
          !(e instanceof ServerPlayer)
              || e.level()
                  .noCollision(
                      e,
                      e.getBoundingBox()
                          .move(destinationX - e.getX(), destinationY - e.getY(), z - e.getZ()));
      if (clear) {
        y = destinationY;
        x = destinationX;
        velocity = new Vec3(velocity.x, -velocity.y, velocity.z);
      } else {
        y = d.bottom() + .001;
        velocity = new Vec3(velocity.x, 0, velocity.z);
        bounce = false;
      }
      e.fallDistance = 0;
    }
    if (x == e.getX() && z == e.getZ() && y == e.getY()) return;
    double shiftX = x - e.getX(), shiftY = y - e.getY(), shiftZ = z - e.getZ();
    if (e instanceof net.minecraft.world.entity.Mob mob) {
      var path = mob.getNavigation().getPath();
      if (path != null)
        for (int i = 0; i < path.getNodeCount(); i++) {
          var old = path.getNode(i);
          var node =
              new net.minecraft.world.level.pathfinder.Node(
                  old.x + (int) shiftX, old.y + (int) shiftY, old.z + (int) shiftZ);
          node.type = old.type;
          node.costMalus = old.costMalus;
          path.replaceNode(i, node);
        }
    }
    if (e instanceof ServerPlayer p) {
      p.serverLevel().getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
      // Zero relative rotation keeps mouse input made while the packet is in flight.
      p.connection.teleport(
          x,
          y,
          z,
          p.getYRot(),
          p.getXRot(),
          Set.of(
              net.minecraft.world.entity.RelativeMovement.X_ROT,
              net.minecraft.world.entity.RelativeMovement.Y_ROT));
      p.setDeltaMovement(velocity);
      p.serverLevel().getChunkSource().move(p);
      p.connection.resetPosition();
      PacketDistributor.sendToPlayer(
          p, new PlanetNetwork.Velocity(velocity.x, velocity.y, velocity.z, bounce));
    } else if (!e.level().isClientSide) {
      e.teleportTo(x, y, z);
      e.setDeltaMovement(velocity);
      e.hasImpulse = true;
      for (var passenger : e.getIndirectPassengers())
        if (passenger instanceof ServerPlayer rider) {
          rider.connection.send(new ClientboundMoveVehiclePacket(e));
          rider.connection.resetPosition();
          rider.serverLevel().getChunkSource().move(rider);
        }
    }
  }

  public static void tick(ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels()) {
      var d = Planet.of(level);
      if (d == null) continue;
      Set<Long> wanted = new HashSet<>();
      var active = TICKETS.computeIfAbsent(level, k -> new HashSet<>());
      int generating = 0;
      for (long key : active) {
        var c = new ChunkPos(key);
        if (level.getChunkSource().getChunkNow(c.x, c.z) == null) generating++;
      }
      int requests = 4;
      for (var player : level.players()) {
        wrap(player);
        if (player.tickCount % 5 == 0 && BOTTOM_VIEW.containsKey(player)) {
          int viewRange =
              Math.min(
                  player.requestedViewDistance(), player.server.getPlayerList().getViewDistance());
          if (BOTTOM_VIEW.get(player).expires < player.server.getTickCount()
              || !BottomView.looking(
                  level,
                  player,
                  player.getEyePosition(),
                  player.getViewVector(1),
                  viewRange,
                  BOTTOM_VIEW.get(player).projection)) BOTTOM_VIEW.remove(player);
        }
        if (player.tickCount % 20 == 0) PlanetNetwork.sync(player);
        int range =
            Math.min(
                player.requestedViewDistance(),
                event.getServer().getPlayerList().getViewDistance());
        range = Math.min(range, d.size() / 32);
        Set<Long> near = new HashSet<>();
        int cx = player.chunkPosition().x, cz = player.chunkPosition().z;
        for (int dx = -range; dx <= range; dx++)
          for (int dz = -range; dz <= range; dz++)
            near.add(ChunkPos.asLong(d.chunk(cx + dx), d.chunk(cz + dz)));
        int bottomRange = bottomRange(player);
        if (bottomRange >= 0) {
          int opposite = (int) Math.floor(player.getX() + d.size() / 2.0) >> 4;
          for (int dx = -bottomRange; dx <= bottomRange; dx++)
            for (int dz = -bottomRange; dz <= bottomRange; dz++)
              near.add(ChunkPos.asLong(d.chunk(opposite + dx), d.chunk(cz + dz)));
        }
        wanted.addAll(near);
        var sent = SENT.computeIfAbsent(player, k -> new HashSet<>());
        for (long key : new HashSet<>(sent))
          if (!near.contains(key)) {
            var c = new ChunkPos(key);
            PacketDistributor.sendToPlayer(player, new PlanetNetwork.ForgetChunk(c.x, c.z));
            sent.remove(key);
          }
        var queue = new ArrayList<>(near);
        queue.sort(
            Comparator.comparingDouble(
                key -> {
                  var c = new ChunkPos(key);
                  double direct =
                      d.delta(
                              player.position(),
                              new Vec3(c.getMinBlockX() + 8, player.getY(), c.getMinBlockZ() + 8))
                          .lengthSqr();
                  if (bottomRange < 0) return direct;
                  var other =
                      new Vec3(player.getX() + d.size() / 2.0, player.getY(), player.getZ());
                  return Math.min(
                      direct,
                      d.delta(
                              other,
                              new Vec3(c.getMinBlockX() + 8, player.getY(), c.getMinBlockZ() + 8))
                          .lengthSqr());
                }));
        int budget = 6;
        for (long key : queue) {
          if (sent.contains(key)) continue;
          var c = new ChunkPos(key);
          if (!active.contains(key)) {
            if (requests == 0 || generating >= 16) continue;
            level.getChunkSource().addRegionTicket(PERIODIC, c, 2, c, true);
            active.add(key);
            requests--;
            generating++;
          }
          var chunk = level.getChunkSource().getChunkNow(c.x, c.z);
          // Tickets schedule generation. Never wait for a new terrain chunk on the server tick.
          if (chunk == null) continue;
          player.connection.send(
              new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
          sent.add(key);
          if (--budget == 0) break;
        }
      }
      for (var iterator = active.iterator(); iterator.hasNext(); ) {
        long key = iterator.next();
        if (!wanted.contains(key)) {
          var c = new ChunkPos(key);
          level.getChunkSource().removeRegionTicket(PERIODIC, c, 2, c, true);
          iterator.remove();
        }
      }
      // Relocation can change the visible-entity index. Iterate a stable snapshot.
      var entities = new ArrayList<Entity>();
      for (var entity : level.getAllEntities()) if (entity != null) entities.add(entity);
      for (var entity : entities) if (!(entity instanceof ServerPlayer)) wrap(entity);
      var changes = CHANGED.remove(level);
      if (changes != null)
        for (var p : changes)
          for (var player : level.players())
            if (tracked(player, p.getX() >> 4, p.getZ() >> 4)) {
              player.connection.send(new ClientboundBlockUpdatePacket(level, p));
              var be = level.getBlockEntity(p);
              if (be != null && be.getUpdatePacket() != null)
                player.connection.send(be.getUpdatePacket());
            }
    }
    SENT.keySet().removeIf(p -> Planet.of(p.level()) == null || p.hasDisconnected());
    BOTTOM_VIEW.keySet().removeIf(p -> Planet.of(p.level()) == null || p.hasDisconnected());
  }
}
