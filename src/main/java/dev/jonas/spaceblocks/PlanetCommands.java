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
                                          + s.fallthrough),
                          false);
                  return 1;
                }));
    for (String option : new String[] {"realistic_gravity", "centrifugal", "fallthrough"})
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
    var p = source.getPlayerOrException();
    var level = source.getServer().getLevel(small ? SpaceBlocks.SMALL : SpaceBlocks.LARGE);
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
    p.teleportTo(level, .5, Planet.SURFACE + 2, .5, Set.of(), 0, 0);
    p.setDeltaMovement(Vec3.ZERO);
    setFlight(p, false);
    PlanetNetwork.sync(p);
    PlanetServer.reset(p);
    return 1;
  }

  public static int leave(CommandSourceStack source)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = source.getPlayerOrException();
    if (Planet.of(p.level()) == null) return 0;
    var data = p.getPersistentData();
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
