package dev.jonas.spaceblocks;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Separate, persistent test world. Fixtures are built once, with bounded work. */
public final class PlanetLab {
  private record Placement(BlockPos position, BlockState state) {}

  private record Build(ServerPlayer player, List<Placement> blocks, Set<ChunkPos> chunks) {}

  private static final Map<ServerLevel, Build> jobs = new WeakHashMap<>();
  private static final TicketType<ChunkPos> TICKET =
      TicketType.create("spaceblocks_lab", Comparator.comparingLong(ChunkPos::toLong));

  private static void block(List<Placement> out, int x, int y, int z, BlockState state) {
    out.add(new Placement(Planet.LAB.canonical(new BlockPos(x, y, z)), state));
  }

  public static int prepare(ServerPlayer p) {
    if (!p.level().dimension().equals(SpaceBlocks.LAB)) return 0;
    var s = PlanetSettings.get(p.level());
    if (s.hasLab) {
      dock(p);
      guide(p);
      return 1;
    }
    if (jobs.containsKey(p.serverLevel())) return 0;
    var blocks = new ArrayList<Placement>();
    // Flight deck and separate TNT towers. Natural ground and caves remain below.
    for (int x = -8; x <= 32; x++)
      for (int z = -5; z <= 5; z++)
        block(blocks, x, 180, z, Blocks.SMOOTH_STONE.defaultBlockState());
    for (int x : new int[] {16, 24, 32}) {
      int top = 184 + (x - 16) * 2;
      for (int y = 181; y < top; y++)
        block(blocks, x, y, 0, Blocks.STONE_BRICKS.defaultBlockState());
      block(blocks, x, top, 0, Blocks.TNT.defaultBlockState());
    }
    block(blocks, -5, 181, 0, Blocks.CHEST.defaultBlockState());
    block(blocks, -5, 181, 3, Blocks.DIAMOND_BLOCK.defaultBlockState());
    block(blocks, -5, 181, -3, Blocks.GLASS.defaultBlockState());
    for (int axis = 0; axis < 2; axis++)
      for (int i = -5; i <= 5; i++)
        for (int width = -2; width <= 2; width++)
          block(
              blocks,
              axis == 0 ? Planet.LAB.size() / 2 + i : width,
              180,
              axis == 0 ? width : Planet.LAB.size() / 2 + i,
              Blocks.STONE_BRICKS.defaultBlockState());
    var chunks = new HashSet<ChunkPos>();
    for (var b : blocks) chunks.add(new ChunkPos(b.position));
    for (var c : chunks) p.serverLevel().getChunkSource().addRegionTicket(TICKET, c, 0, c);
    jobs.put(p.serverLevel(), new Build(p, blocks, chunks));
    p.sendSystemMessage(
        Component.literal(
            "Preparing isolated test planet: natural terrain/caves, flight deck, TNT towers and"
                + " seam platforms..."));
    return 1;
  }

  private static void dock(ServerPlayer p) {
    p.connection.teleport(6.5, 182, .5, 0, 0);
    p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    p.getAbilities().mayfly = true;
    p.getAbilities().flying = true;
    p.onUpdateAbilities();
    PlanetServer.reset(p);
  }

  private static void guide(ServerPlayer p) {
    PlanetSatellite.ensureLab(p);
    p.sendSystemMessage(
        Component.literal(
            "Test lab ready. /planet map; /planet satellite launch; /planet tunnel create then"
                + " /planet tunnel drop; /planet surface. TNT towers: X=16/24/32. Seam platforms: X"
                + " or Z=416, Y=180. Terrain and caves below. /planet leave returns home."));
  }

  public static void tick(ServerTickEvent.Post event) {
    for (var it = jobs.entrySet().iterator(); it.hasNext(); ) {
      var e = it.next();
      var level = e.getKey();
      var b = e.getValue();
      if (b.player.hasDisconnected() || b.player.level() != level) {
        for (var c : b.chunks) level.getChunkSource().removeRegionTicket(TICKET, c, 0, c);
        it.remove();
        continue;
      }
      if (b.chunks.stream().anyMatch(c -> level.getChunkSource().getChunkNow(c.x, c.z) == null))
        continue;
      long deadline = System.nanoTime() + 2_000_000;
      for (int i = 0; i < 128 && !b.blocks.isEmpty(); i++) {
        var placed = b.blocks.removeLast();
        level.setBlock(placed.position, placed.state, 3);
        if (System.nanoTime() > deadline) break;
      }
      if (b.blocks.isEmpty()) {
        var settings = PlanetSettings.get(level);
        settings.hasLab = true;
        settings.setDirty();
        dock(b.player);
        guide(b.player);
        for (var c : b.chunks) level.getChunkSource().removeRegionTicket(TICKET, c, 0, c);
        it.remove();
      }
    }
  }

  public static void stopped(ServerStoppedEvent event) {
    jobs.clear();
  }
}
