package dev.jonas.spaceblocks;

import static net.minecraft.commands.Commands.literal;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import dev.jonas.spaceblocks.physics.RadialMotion;

public final class SpaceCommands {
    private static final String RETURN_KEY = "spaceblocks_return";

    private SpaceCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("planet").requires(source -> source.hasPermission(2))
                .executes(context -> dev.jonas.spaceblocks.surface.FlatMotion.enter(context.getSource(),false))
                .then(literal("flat").executes(context -> dev.jonas.spaceblocks.surface.FlatMotion.enter(context.getSource(),SpaceBlocks.FLAT)))
                .then(literal("normal").executes(context -> dev.jonas.spaceblocks.surface.FlatMotion.enter(context.getSource(),false)))
                .then(literal("small").executes(context -> dev.jonas.spaceblocks.surface.FlatMotion.enter(context.getSource(),true)))
                .then(literal("colors").executes(context -> dev.jonas.spaceblocks.surface.FlatMotion.enter(context.getSource(),SpaceBlocks.FLAT_COLORS)))
                .then(literal("view").executes(context -> move(context.getSource(),true)))
                .then(literal("surface").executes(context -> move(context.getSource(),false)))
                .then(literal("leave").executes(context -> leave(context.getSource())))
                .then(literal("fly").executes(context -> mode(context.getSource(),true)))
                .then(literal("walk").executes(context -> mode(context.getSource(),false)))
                .then(literal("info").executes(context -> info(context.getSource()))));
        event.getDispatcher().register(literal("space").requires(source -> source.hasPermission(2))
                .executes(context -> help(context.getSource()))
                .then(literal("enter").executes(context -> dev.jonas.spaceblocks.surface.FlatMotion.enter(context.getSource(),false)))
                .then(literal("flat_test").executes(context -> dev.jonas.spaceblocks.surface.FlatMotion.enter(context.getSource(),true)))
                .then(literal("colors").executes(context -> dev.jonas.spaceblocks.surface.FlatMotion.enter(context.getSource(),SpaceBlocks.FLAT_COLORS)))
                .then(literal("radial").executes(context -> enter(context.getSource())))
                .then(literal("legacy").executes(context -> legacy(context.getSource())))
                .then(literal("leave").executes(context -> leave(context.getSource())))
                .then(literal("view").executes(context -> move(context.getSource(), true)))
                .then(literal("surface").executes(context -> move(context.getSource(), false)))
                .then(literal("walk").executes(context -> mode(context.getSource(), false)))
                .then(literal("fly").executes(context -> mode(context.getSource(), true)))
                .then(literal("info").executes(context -> info(context.getSource())))
                .then(literal("verify").executes(context -> verify(context.getSource()))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Space Blocks 0.5 | /planet: planeta; view: vista distante; surface: chão; fly: voo; walk: caminhar; info; leave: voltar."), false);
        return 1;
    }

    private static ServerLevel space(CommandSourceStack source) {
        return source.getServer().getLevel(SpaceBlocks.SPACE);
    }

    public static void remember(ServerPlayer player) {
        if (player.getPersistentData().contains(RETURN_KEY)) return;
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yaw", player.getYRot());
        tag.putFloat("pitch", player.getXRot());
        tag.putBoolean("mayfly", player.getAbilities().mayfly);
        tag.putBoolean("flying", player.getAbilities().flying);
        tag.putBoolean("no_gravity", player.isNoGravity());
        player.getPersistentData().put(RETURN_KEY, tag);
    }

    private static void restore(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(RETURN_KEY);
        boolean creative = player.isCreative() || player.isSpectator();
        player.getAbilities().mayfly = creative || tag.getBoolean("mayfly");
        player.getAbilities().flying = player.getAbilities().mayfly && tag.getBoolean("flying");
        player.setNoGravity(player.isSpectator() || tag.getBoolean("no_gravity"));
        player.fallDistance = 0;
        player.onUpdateAbilities();
        player.getPersistentData().remove(RETURN_KEY);
        player.getPersistentData().remove(RadialMotion.FLIGHT_KEY);
        player.getPersistentData().remove(dev.jonas.spaceblocks.surface.FlatMotion.FLIGHT);
        RadialMotion.forget(player);
    }

    private static int enter(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel target = space(source);
        if (target == null) {
            source.sendFailure(Component.literal("Dimensão spaceblocks:space_large não encontrada. Confira os datapacks do mundo."));
            return 0;
        }
        if (!SpaceBlocks.isSpace(player.level().dimension()) && !SpaceBlocks.isFlat(player.level().dimension())) remember(player);
        teleportToPlanet(player, target, false);
        source.sendSuccess(() -> Component.literal("Gravidade radial ativa: WASD para andar, Espaço para pular, Ctrl para correr. /planet fly: voo livre. /planet view: vista distante. /planet leave: voltar."), false);
        return 1;
    }

    private static int legacy(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel target = source.getServer().getLevel(SpaceBlocks.LEGACY_SPACE);
        if (target == null) return 0;
        if (!SpaceBlocks.isSpace(player.level().dimension()) && !SpaceBlocks.isFlat(player.level().dimension())) remember(player);
        teleportToPlanet(player, target, false);
        source.sendSuccess(() -> Component.literal("Planeta antigo preservado. /planet abre o planeta maior."), false);
        return 1;
    }

    private static void teleportToPlanet(ServerPlayer player, ServerLevel target, boolean distant) {
        if (!(target.getChunkSource().getGenerator() instanceof PlanetChunkGenerator generator)) return;
        PlanetDefinition body = generator.planet();
        double x = body.centerX() + 0.5;
        double y = body.centerY() + body.radius() + 12;
        double z = body.centerZ() + 0.5;
        if (distant) {
            y = body.centerY() + body.radius() * 0.4;
            z = body.centerZ() + body.radius() * 2.5;
        }
        // Preload the landing chunk before the teleport packet is sent.
        target.getChunk(net.minecraft.util.Mth.floor(x) >> 4, net.minecraft.util.Mth.floor(z) >> 4);
        player.teleportTo(target, x, y, z, java.util.Set.of(), distant ? 180 : 0, distant ? 10 : 0);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.getPersistentData().remove("spaceblocks_north_x");
        player.getPersistentData().remove("spaceblocks_north_y");
        player.getPersistentData().remove("spaceblocks_north_z");
        RadialMotion.reset(player,distant);
        player.onUpdateAbilities();
    }

    private static int move(CommandSourceStack source, boolean distant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (SpaceBlocks.isFlat(player.level().dimension())) return dev.jonas.spaceblocks.surface.FlatMotion.move(source,distant);
        if (!SpaceBlocks.isSpace(player.level().dimension())) return dev.jonas.spaceblocks.surface.FlatMotion.enter(source,false);
        teleportToPlanet(player, player.serverLevel(), distant);
        return 1;
    }

    private static int mode(CommandSourceStack source,boolean flight) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        if(SpaceBlocks.isFlat(player.level().dimension())) { dev.jonas.spaceblocks.surface.FlatMotion.setFlight(player,flight);return 1; }
        if(!SpaceBlocks.isSpace(player.level().dimension())) {
            source.sendFailure(Component.literal("Entre no planeta com /planet primeiro."));
            return 0;
        }
        RadialMotion.reset(player,flight);
        source.sendSuccess(()->Component.literal(flight?"Voo livre ativo. /planet walk restaura a gravidade radial.":"Gravidade radial e caminhada ativas."),false);
        return 1;
    }

    private static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!player.getPersistentData().contains(RETURN_KEY)) {
            source.sendFailure(Component.literal("Nenhum ponto de retorno registrado. Entre usando /planet."));
            return 0;
        }
        CompoundTag tag = player.getPersistentData().getCompound(RETURN_KEY);
        ResourceLocation location = ResourceLocation.tryParse(tag.getString("dimension"));
        ServerLevel target = location == null ? null : source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, location));
        if (target == null) {
            source.sendFailure(Component.literal("A dimensão de origem não está disponível. O ponto de retorno foi preservado."));
            return 0;
        }
        player.teleportTo(target, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                java.util.Set.of(), tag.getFloat("yaw"), tag.getFloat("pitch"));
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        restore(player);
        source.sendSuccess(() -> Component.literal("Você voltou ao ponto de origem."), false);
        return 1;
    }

    private static int info(CommandSourceStack source) {
        if(source.getEntity() instanceof ServerPlayer p && SpaceBlocks.isFlat(p.level().dimension())) {
            var d=dev.jonas.spaceblocks.surface.FlatMotion.generator(p.serverLevel()).surface();
            var position=d.locate(p.getX(),p.getZ());
            source.sendSuccess(()->Component.literal(String.format(java.util.Locale.ROOT,"Space Blocks 0.5 | face %s | lado %d blocos | volta equatorial %d blocos | altitude %.1f | gravidade normal",position==null?"?":position.face(),d.faceSize(),d.faceSize()*4,p.getY()-(position==null?65:d.height(position)+1))),false);
            return 1;
        }
        ServerLevel target = source.getEntity() instanceof ServerPlayer player && SpaceBlocks.isSpace(player.level().dimension())
                ? player.serverLevel() : space(source);
        if (target == null || !(target.getChunkSource().getGenerator() instanceof PlanetChunkGenerator generator)) return 0;
        PlanetDefinition body = generator.planet();
        double altitude = -body.depth(source.getPosition().x, source.getPosition().y, source.getPosition().z);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Space Blocks 0.3 | raio %.0f | seed %d | altitude radial %.1f | modo: %s",
                body.radius(), body.seed(), altitude,source.getEntity()!=null && RadialMotion.active(source.getEntity())?"gravidade radial":"voo livre")), false);
        return 1;
    }

    /** Exercises actual chunk generation, useful from a dedicated-server console. */
    public static int verify(CommandSourceStack source) {
        ServerLevel target = space(source);
        if (target == null || !(target.getChunkSource().getGenerator() instanceof PlanetChunkGenerator generator)) {
            source.sendFailure(Component.literal("SPACEBLOCKS_VERIFY_FAILED: missing dimension or generator"));
            return 0;
        }
        PlanetDefinition body = generator.planet();
        int cx = net.minecraft.util.Mth.floor(body.centerX());
        int cy = net.minecraft.util.Mth.floor(body.centerY());
        int cz = net.minecraft.util.Mth.floor(body.centerZ());
        boolean core = target.getBlockState(new net.minecraft.core.BlockPos(cx, cy, cz)).is(net.minecraft.world.level.block.Blocks.STONE);
        boolean vacuum = target.getBlockState(new net.minecraft.core.BlockPos(cx + (int) body.radius() + 16, cy, cz)).isAir();
        boolean allDirections = true;
        int[][] axes = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] axis : axes) {
            int inside = (int) body.radius() - 8;
            net.minecraft.core.BlockPos sample = new net.minecraft.core.BlockPos(cx + axis[0] * inside, cy + axis[1] * inside, cz + axis[2] * inside);
            allDirections &= !target.getBlockState(sample).isAir();
        }
        if (!core || !vacuum || !allDirections) {
            source.sendFailure(Component.literal("SPACEBLOCKS_VERIFY_FAILED: core=" + core + " vacuum=" + vacuum + " axes=" + allDirections));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("SPACEBLOCKS_VERIFY_OK: dimension loaded, solid core, six hemispheres and exterior vacuum verified."), false);
        return 1;
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (SpaceBlocks.isFlat(player.level().dimension())) return;
        if (SpaceBlocks.isSpace(player.level().dimension())) {
            if (!player.getPersistentData().contains(RETURN_KEY)) {
                // Direct dimension commands have no entry checkpoint; supply a safe Overworld return.
                CompoundTag tag = new CompoundTag();
                ServerLevel overworld = player.server.overworld();
                net.minecraft.core.BlockPos spawn = overworld.getSharedSpawnPos();
                tag.putString("dimension", Level.OVERWORLD.location().toString());
                tag.putDouble("x", spawn.getX() + 0.5);
                tag.putDouble("y", spawn.getY() + 1);
                tag.putDouble("z", spawn.getZ() + 0.5);
                tag.putBoolean("mayfly", player.getAbilities().mayfly);
                tag.putBoolean("flying", player.getAbilities().flying);
                player.getPersistentData().put(RETURN_KEY, tag);
            }
            RadialMotion.applyAbilities(player,player.getPersistentData().getBoolean(RadialMotion.FLIGHT_KEY));
            if (player.getY() < player.level().getMinBuildHeight() + 8 || player.getY() > player.level().getMaxBuildHeight() - 8) {
                teleportToPlanet(player, player.serverLevel(), true);
                player.displayClientMessage(Component.literal("Limite vertical do protótipo: retorno à vista do planeta."), true);
            }
        } else if (player.getPersistentData().contains(RETURN_KEY)) {
            restore(player);
        }
    }
}
