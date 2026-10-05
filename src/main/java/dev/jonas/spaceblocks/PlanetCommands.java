package dev.jonas.spaceblocks;

import static net.minecraft.commands.Commands.*;

import com.mojang.brigadier.arguments.BoolArgumentType;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class PlanetCommands {
  public static void register(RegisterCommandsEvent event) {
    var root =
        literal("planet")
            .requires(s -> s.hasPermission(2))
            .executes(c -> enter(c.getSource(), false));
    root.then(literal("large").executes(c -> enter(c.getSource(), false)));
    root.then(literal("small").executes(c -> enter(c.getSource(), true)));
    root.then(literal("natural").executes(c -> enterDimension(c.getSource(), SpaceBlocks.NATURAL)));
    root.then(literal("flat").executes(c -> enterDimension(c.getSource(), SpaceBlocks.LARGE)));
    root.then(
        literal("lab")
            .executes(
                c -> {
                  enterDimension(c.getSource(), SpaceBlocks.LAB);
                  return PlanetLab.prepare(c.getSource().getPlayerOrException());
                }));
    root.then(
        literal("planets")
            .executes(
                c -> {
                  PlanetManagerNetwork.send(c.getSource().getPlayerOrException());
                  return 1;
                }));
    root.then(
        literal("generate")
            .then(
                argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .then(
                        argument(
                                "radius",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(
                                    32, 1024))
                            .executes(
                                c ->
                                    PlanetCatalog.generate(
                                        c.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(
                                            c, "name"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(c, "radius"),
                                        null))
                            .then(
                                argument(
                                        "seed",
                                        com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                    .executes(
                                        c ->
                                            PlanetCatalog.generate(
                                                c.getSource().getPlayerOrException(),
                                                com.mojang.brigadier.arguments.StringArgumentType
                                                    .getString(c, "name"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                    .getInteger(c, "radius"),
                                                com.mojang.brigadier.arguments.LongArgumentType
                                                    .getLong(c, "seed")))))));
    root.then(
        literal("enter")
            .then(
                argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(
                        c ->
                            PlanetCatalog.teleport(
                                c.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(
                                    c, "name"),
                                0,
                                0,
                                null))));
    root.then(
        literal("satellite")
            .then(
                literal("launch")
                    .executes(
                        c -> PlanetSatellite.launch(c.getSource().getPlayerOrException(), 128, 1))
                    .then(
                        argument(
                                "altitude",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(16, 400))
                            .executes(
                                c ->
                                    PlanetSatellite.launch(
                                        c.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(c, "altitude"),
                                        1))
                            .then(
                                argument(
                                        "speed",
                                        com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(
                                            .1, 3))
                                    .executes(
                                        c ->
                                            PlanetSatellite.launch(
                                                c.getSource().getPlayerOrException(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                    .getInteger(c, "altitude"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType
                                                    .getDouble(c, "speed"))))))
            .then(
                literal("info")
                    .executes(c -> PlanetSatellite.info(c.getSource().getPlayerOrException())))
            .then(
                literal("remove")
                    .executes(c -> PlanetSatellite.remove(c.getSource().getPlayerOrException()))));
    root.then(
        literal("map")
            .executes(
                c -> {
                  PlanetAtlas.request(c.getSource().getPlayerOrException());
                  return 1;
                }));
    root.then(
        literal("tunnel")
            .then(
                literal("create")
                    .executes(c -> PlanetTunnel.create(c.getSource().getPlayerOrException())))
            .then(
                literal("drop")
                    .executes(c -> PlanetTunnel.drop(c.getSource().getPlayerOrException()))));
    root.then(literal("core").executes(c -> core(c.getSource())));
    root.then(literal("surface").executes(c -> surface(c.getSource())));
    root.then(
        literal("noclip")
            .then(
                argument("enabled", BoolArgumentType.bool())
                    .executes(c -> noclip(c.getSource(), BoolArgumentType.getBool(c, "enabled")))));
    root.then(literal("leave").executes(c -> leave(c.getSource())));
    root.then(
        literal("fly")
            .executes(c -> flight(c.getSource(), true))
            .then(
                argument("enabled", BoolArgumentType.bool())
                    .executes(c -> flight(c.getSource(), BoolArgumentType.getBool(c, "enabled")))));
    root.then(literal("walk").executes(c -> flight(c.getSource(), false)));
    root.then(
        literal("info")
            .executes(
                c -> {
                  var p = c.getSource().getPlayerOrException();
                  var d = Planet.of(p.level());
                  var s = PlanetSettings.get(p.level());
                  c.getSource()
                      .sendSuccess(
                          () ->
                              Component.literal(
                                  d == null
                                      ? "Standard world"
                                      : "Periodic map "
                                          + d.size()
                                          + " x "
                                          + d.size()
                                          + "; radius="
                                          + d.radius()
                                          + "; bottom="
                                          + d.bottom()
                                          + "; position="
                                          + p.blockPosition()
                                          + "; realistic_gravity="
                                          + s.realisticGravity
                                          + "; centrifugal="
                                          + s.centrifugal
                                          + "; fallthrough="
                                          + s.fallthrough
                                          + "; air_drag="
                                          + s.airDrag),
                          false);
                  return 1;
                }));
    for (String option :
        new String[] {"realistic_gravity", "centrifugal", "fallthrough", "air_drag"})
      root.then(
          literal("physics")
              .then(
                  literal(option)
                      .then(
                          argument("enabled", BoolArgumentType.bool())
                              .executes(
                                  c -> {
                                    var p = c.getSource().getPlayerOrException();
                                    if (Planet.of(p.level()) == null) {
                                      c.getSource()
                                          .sendFailure(Component.literal("Enter a planet first."));
                                      return 0;
                                    }
                                    var s = PlanetSettings.get(p.level());
                                    boolean value = BoolArgumentType.getBool(c, "enabled");
                                    switch (option) {
                                      case "realistic_gravity" -> s.realisticGravity = value;
                                      case "centrifugal" -> s.centrifugal = value;
                                      case "fallthrough" -> s.fallthrough = value;
                                      case "air_drag" -> s.airDrag = value;
                                    }
                                    s.setDirty();
                                    for (var player : p.serverLevel().players())
                                      PlanetNetwork.sync(player);
                                    c.getSource()
                                        .sendSuccess(
                                            () -> Component.literal(option + "=" + value), false);
                                    return 1;
                                  }))));
    event.getDispatcher().register(root);
  }

  public static int enter(CommandSourceStack source, boolean small)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    return enterDimension(source, small ? SpaceBlocks.SMALL : SpaceBlocks.NATURAL);
  }

  public static int enterDimension(
      CommandSourceStack source,
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = source.getPlayerOrException();
    var level = source.getServer().getLevel(dimension);
    if (level == null) {
      source.sendFailure(Component.literal("Planet dimension unavailable."));
      return 0;
    }
    if (Planet.of(p.level()) == null) {
      var data = p.getPersistentData();
      data.putString("planet_return_dimension", p.level().dimension().location().toString());
      data.putDouble("planet_return_x", p.getX());
      data.putDouble("planet_return_y", p.getY());
      data.putDouble("planet_return_z", p.getZ());
      data.putFloat("planet_return_yaw", p.getYRot());
      data.putFloat("planet_return_pitch", p.getXRot());
      data.putBoolean("planet_return_mayfly", p.getAbilities().mayfly);
      data.putBoolean("planet_return_flying", p.getAbilities().flying);
    }
    level.getChunk(0, 0);
    int spawnX = 0;
    var saved = PlanetSettings.get(level);
    var planet = Planet.of(level);
    if (saved.hasTunnel
        && Math.abs(PeriodicMath.wrap(saved.tunnelZ, planet.size())) <= 2
        && (Math.abs(PeriodicMath.wrap(saved.tunnelX, planet.size())) <= 2
            || Math.abs(PeriodicMath.wrap(saved.tunnelX + planet.size() / 2, planet.size()))
                <= 2)) {
      spawnX = 4;
    }
    int spawnY =
        level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spawnX,
                0)
            + 1;
    while (spawnY < Planet.SURFACE && spawnX < 32) {
      spawnX += 4;
      spawnY =
          level.getHeight(
                  net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                  spawnX,
                  0)
              + 1;
    }
    p.teleportTo(level, spawnX + .5, spawnY, .5, Set.of(), 0, 0);
    p.setDeltaMovement(Vec3.ZERO);
    setFlight(p, false);
    PlanetNetwork.sync(p);
    PlanetServer.reset(p);
    return 1;
  }

  private static int core(CommandSourceStack source)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = source.getPlayerOrException();
    if (Planet.of(p.level()) == null) {
      source.sendFailure(Component.literal("Enter a planet first."));
      return 0;
    }
    var settings = PlanetSettings.get(p.level());
    settings.fallthrough = false;
    settings.setDirty();
    int y = -472;
    var level = p.serverLevel();
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        for (int dy = 0; dy <= 4; dy++)
          level.setBlock(
              new net.minecraft.core.BlockPos(x, y + dy, z),
              (dy == 0 || dy == 4 || Math.abs(x) == 2 || Math.abs(z) == 2)
                  ? net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState()
                  : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
              3);
    p.connection.teleport(.5, y + 1, .5, p.getYRot(), p.getXRot());
    p.setDeltaMovement(Vec3.ZERO);
    setFlight(p, true);
    for (var player : level.players()) PlanetNetwork.sync(player);
    source.sendSuccess(
        () ->
            Component.literal(
                "Deep core chamber. Fallthrough disabled. The exponential projection has no finite"
                    + " geometric center. Use /planet surface to return."),
        false);
    return 1;
  }

  private static int surface(CommandSourceStack source)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = source.getPlayerOrException();
    if (Planet.of(p.level()) == null) return 0;
    int x = p.blockPosition().getX(), z = p.blockPosition().getZ();
    var planet = Planet.of(p.level());
    var settings = PlanetSettings.get(p.level());
    if (settings.hasTunnel
        && Math.abs(PeriodicMath.wrap(z - settings.tunnelZ, planet.size())) <= 2
        && (Math.abs(PeriodicMath.wrap(x - settings.tunnelX, planet.size())) <= 2
            || Math.abs(PeriodicMath.wrap(x - settings.tunnelX - planet.size() / 2, planet.size()))
                <= 2)) {
      x = PeriodicMath.wrap(x + 4, planet.size());
    }
    int y =
        p.serverLevel()
                .getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x,
                    z)
            + 1;
    p.connection.teleport(x + .5, y, z + .5, p.getYRot(), p.getXRot());
    p.setDeltaMovement(Vec3.ZERO);
    return 1;
  }

  private static int noclip(CommandSourceStack source, boolean enabled)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = source.getPlayerOrException();
    if (Planet.of(p.level()) == null) {
      source.sendFailure(Component.literal("Enter a planet first."));
      return 0;
    }
    var data = p.getPersistentData();
    if (enabled) {
      if (!data.contains("planet_noclip_mode"))
        data.putString("planet_noclip_mode", p.gameMode.getGameModeForPlayer().getName());
      p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
      var settings = PlanetSettings.get(p.level());
      settings.fallthrough = false;
      settings.setDirty();
      for (var player : p.serverLevel().players()) PlanetNetwork.sync(player);
    } else if (data.contains("planet_noclip_mode")) {
      // Return to a safe surface before restoring physical collisions.
      surface(source);
      p.setGameMode(
          net.minecraft.world.level.GameType.byName(data.getString("planet_noclip_mode")));
      data.remove("planet_noclip_mode");
    }
    source.sendSuccess(() -> Component.literal("noclip=" + enabled), false);
    return 1;
  }

  public static int leave(CommandSourceStack source)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = source.getPlayerOrException();
    if (Planet.of(p.level()) == null) return 0;
    var data = p.getPersistentData();
    if (data.contains("planet_noclip_mode")) {
      p.setGameMode(
          net.minecraft.world.level.GameType.byName(data.getString("planet_noclip_mode")));
      data.remove("planet_noclip_mode");
    }
    var target =
        net.minecraft.resources.ResourceLocation.tryParse(
            data.getString("planet_return_dimension"));
    var level =
        target == null
            ? source.getServer().overworld()
            : source
                .getServer()
                .getLevel(
                    net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, target));
    if (level == null) level = source.getServer().overworld();
    double
        x =
            data.contains("planet_return_x")
                ? data.getDouble("planet_return_x")
                : level.getSharedSpawnPos().getX() + .5,
        y =
            data.contains("planet_return_y")
                ? data.getDouble("planet_return_y")
                : level.getSharedSpawnPos().getY() + 1,
        z =
            data.contains("planet_return_z")
                ? data.getDouble("planet_return_z")
                : level.getSharedSpawnPos().getZ() + .5;
    p.teleportTo(
        level,
        x,
        y,
        z,
        Set.of(),
        data.getFloat("planet_return_yaw"),
        data.getFloat("planet_return_pitch"));
    p.setNoGravity(false);
    p.setDeltaMovement(Vec3.ZERO);
    p.fallDistance = 0;
    p.getAbilities().mayfly =
        p.isCreative() || p.isSpectator() || data.getBoolean("planet_return_mayfly");
    p.getAbilities().flying = p.isSpectator() || data.getBoolean("planet_return_flying");
    p.onUpdateAbilities();
    PlanetServer.reset(p);
    return 1;
  }

  private static int flight(CommandSourceStack source, boolean enabled)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = source.getPlayerOrException();
    if (Planet.of(p.level()) == null) {
      source.sendFailure(Component.literal("Enter a planet first."));
      return 0;
    }
    setFlight(p, enabled);
    return 1;
  }

  private static void setFlight(ServerPlayer p, boolean enabled) {
    p.getAbilities().mayfly = enabled || p.isCreative() || p.isSpectator();
    p.getAbilities().flying = enabled || p.isSpectator();
    p.onUpdateAbilities();
    p.fallDistance = 0;
  }
}
