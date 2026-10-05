package dev.jonas.spaceblocks;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A collision-free test probe integrating the same flat variable-gravity/centrifugal formulas. */
public final class PlanetSatellite {
  private record Lease(UUID id, ServerLevel level, Set<ChunkPos> chunks, int created) {}

  private static final Map<UUID, Lease> leases = new HashMap<>();
  private static final TicketType<UUID> TICKET =
      TicketType.create("spaceblocks_satellite", Comparator.<UUID>naturalOrder());

  public static double circularSpeed(double altitude, int radius) {
    return Math.sqrt(
        .08
            * PeriodicMath.gravityCoefficient(altitude, radius)
            * radius
            * Math.exp(altitude / radius)
            / 2);
  }

  public static int launch(ServerPlayer p, int altitude, double multiplier) {
    var planet = Planet.of(p.level());
    if (planet == null) return 0;
    remove(p);
    var settings = PlanetSettings.get(p.level());
    settings.realisticGravity = true;
    settings.centrifugal = true;
    settings.setDirty();
    for (var player : p.serverLevel().players()) PlanetNetwork.sync(player);
    double vx = circularSpeed(altitude, planet.radius()) * multiplier;
    var stand = new ArmorStand(p.serverLevel(), p.getX(), Planet.SURFACE + altitude, p.getZ());
    var marker = new net.minecraft.nbt.CompoundTag();
    marker.putBoolean("Marker", true);
    stand.readAdditionalSaveData(marker);
    stand.setNoGravity(true);
    stand.setInvisible(true);
    stand.setInvulnerable(true);
    stand.setCustomName(Component.literal("Orbit test satellite"));
    stand.setCustomNameVisible(true);
    stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLD_BLOCK));
    stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
    var data = stand.getPersistentData();
    data.putBoolean("planet_satellite", true);
    data.putString("satellite_owner", p.getUUID().toString());
    data.putDouble("satellite_vx", vx);
    data.putDouble("satellite_vy", 0);
    data.putDouble("satellite_distance", 0);
    p.serverLevel().addFreshEntity(stand);
    SatellitePositions.get(p.server)
        .remember(
            new SatellitePositions.Position(
                stand.getUUID(),
                p.level().dimension().location().toString(),
                stand.chunkPosition().x,
                stand.chunkPosition().z));
    p.sendSystemMessage(
        Component.literal(
            String.format(
                Locale.ROOT,
                "Satellite launched: altitude=%d, speed=%.4f blocks/tick, ideal circuit=%.1fs."
                    + " /planet satellite info or remove. Probe has no collisions; this tests"
                    + " Spheretest physics, not a physical sphere.",
                altitude,
                vx,
                planet.size() / vx / 20)));
    return 1;
  }

  public static long savedProbeCount(ServerLevel level) {
    return SatellitePositions.get(level.getServer()).positions.values().stream()
        .filter(p -> p.dimension().equals(level.dimension().location().toString()))
        .count();
  }

  private static void release(Lease lease) {
    for (var c : lease.chunks)
      lease.level.getChunkSource().removeRegionTicket(TICKET, c, 2, lease.id);
  }

  public static void ensureLab(ServerPlayer p) {
    if (!p.level().dimension().equals(SpaceBlocks.LAB)) return;
    for (var e : p.serverLevel().getAllEntities())
      if (e.getPersistentData().getBoolean("planet_satellite")) {
        e.getPersistentData().putBoolean("lab_probe", true);
        return;
      }
    if (savedProbeCount(p.serverLevel()) > 0) return;
    launch(p, 128, 1);
    for (var e : p.serverLevel().getAllEntities())
      if (e.getPersistentData().getBoolean("planet_satellite"))
        e.getPersistentData().putBoolean("lab_probe", true);
  }

  public static int remove(ServerPlayer p) {
    int count = 0;
    for (var e : p.serverLevel().getAllEntities())
      if (e instanceof ArmorStand
          && e.getPersistentData().getBoolean("planet_satellite")
          && (e.getPersistentData().getString("satellite_owner").equals(p.getUUID().toString())
              || p.level().dimension().equals(SpaceBlocks.LAB)
                  && e.getPersistentData().getBoolean("lab_probe"))) {
        var lease = leases.remove(e.getUUID());
        if (lease != null) release(lease);
        SatellitePositions.get(p.server).remove(e.getUUID());
        e.discard();
        count++;
      }
    return count;
  }

  public static int info(ServerPlayer p) {
    for (var e : p.serverLevel().getAllEntities())
      if (e.getPersistentData().getBoolean("planet_satellite")
          && (e.getPersistentData().getString("satellite_owner").equals(p.getUUID().toString())
              || p.level().dimension().equals(SpaceBlocks.LAB)
                  && e.getPersistentData().getBoolean("lab_probe"))) {
        var data = e.getPersistentData();
        p.sendSystemMessage(
            Component.literal(
                String.format(
                    Locale.ROOT,
                    "Satellite X=%.1f Y=%.1f Z=%.1f; vx=%.4f vy=%.4f; circuits=%.3f",
                    e.getX(),
                    e.getY(),
                    e.getZ(),
                    data.getDouble("satellite_vx"),
                    data.getDouble("satellite_vy"),
                    data.getDouble("satellite_distance") / Planet.of(p.level()).size())));
        return 1;
      }
    p.sendSystemMessage(Component.literal("No active satellite here. /planet satellite launch"));
    return 0;
  }

  public static void tick(ServerTickEvent.Post event) {
    var seen = new HashSet<UUID>();
    for (var level : event.getServer().getAllLevels()) {
      var planet = Planet.of(level);
      if (planet == null) continue;
      var settings = PlanetSettings.get(level);
      for (var entity : level.getAllEntities()) {
        if (!(entity instanceof ArmorStand)
            || !entity.getPersistentData().getBoolean("planet_satellite")) continue;
        seen.add(entity.getUUID());
        var data = entity.getPersistentData();
        double vx = data.getDouble("satellite_vx"),
            vy = data.getDouble("satellite_vy"),
            h = entity.getY() - Planet.SURFACE;
        double nextX = PeriodicMath.wrap(entity.getX() + vx, planet.size());
        var chunks = new HashSet<ChunkPos>();
        chunks.add(planet.canonical(entity.chunkPosition()));
        chunks.add(
            planet.canonical(new ChunkPos((int) Math.floor(nextX) >> 4, entity.chunkPosition().z)));
        var old = leases.get(entity.getUUID());
        if (old == null || !old.chunks.equals(chunks)) {
          // Add before removing old tickets so the probe never loses its current chunk.
          for (var c : chunks)
            level.getChunkSource().addRegionTicket(TICKET, c, 2, entity.getUUID());
          if (old != null)
            for (var c : old.chunks)
              if (!chunks.contains(c))
                level.getChunkSource().removeRegionTicket(TICKET, c, 2, entity.getUUID());
          leases.put(
              entity.getUUID(),
              new Lease(entity.getUUID(), level, chunks, event.getServer().getTickCount()));
        }
        if (chunks.stream().anyMatch(c -> level.getChunkSource().getChunkNow(c.x, c.z) == null))
          continue;
        double gravity =
            .08
                * (settings.realisticGravity
                    ? PeriodicMath.gravityCoefficient(h, planet.radius())
                    : 1);
        double centrifugal =
            settings.centrifugal ? PeriodicMath.centrifugal(vx, 0, h, planet.radius()) : 0;
        vy += centrifugal - gravity;
        double y = entity.getY() + vy;
        if (settings.fallthrough && y < planet.bottom()) {
          y = planet.bottom() + 1;
          nextX = PeriodicMath.wrap(nextX + planet.size() / 2., planet.size());
          vy = -vy;
        }
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
          entity.discard();
          continue;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.teleportTo(nextX, y, entity.getZ());
        SatellitePositions.get(event.getServer())
            .remember(
                new SatellitePositions.Position(
                    entity.getUUID(),
                    level.dimension().location().toString(),
                    entity.chunkPosition().x,
                    entity.chunkPosition().z));
        data.putDouble("satellite_vy", vy);
        data.putDouble("satellite_distance", data.getDouble("satellite_distance") + Math.abs(vx));
      }
    }
    for (var it = leases.entrySet().iterator(); it.hasNext(); ) {
      var e = it.next();
      if (!seen.contains(e.getKey())
          && event.getServer().getTickCount() - e.getValue().created > 200) {
        release(e.getValue());
        SatellitePositions.get(event.getServer()).remove(e.getKey());
        it.remove();
      }
    }
  }

  public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
    for (var p : SatellitePositions.get(event.getServer()).positions.values()) {
      var level = event.getServer().getLevel(PlanetCatalog.key(p.dimension()));
      if (level == null) continue;
      var c = new ChunkPos(p.chunkX(), p.chunkZ());
      level.getChunkSource().addRegionTicket(TICKET, c, 2, p.id());
      leases.put(p.id(), new Lease(p.id(), level, Set.of(c), event.getServer().getTickCount()));
    }
  }

  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    leases.clear();
  }
}
