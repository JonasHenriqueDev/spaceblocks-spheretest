package dev.jonas.spaceblocks;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in actual Minecraft-server checks, with persistence checked on a second run. */
public final class PlanetTests {
  private static void check(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  public static void started(ServerStartedEvent e) {
    if (!Boolean.getBoolean("spaceblocks.testServer")) return;
    var results = new ArrayList<String>();
    try {
      if (Boolean.getBoolean("spaceblocks.packagedTest"))
        check(
            SpaceBlocks.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString()
                .contains("spaceblocks-0.8.3.jar"),
            "Final packaged JAR is the loaded mod");
      for (var key : List.of(SpaceBlocks.SMALL, SpaceBlocks.LARGE)) {
        var level = e.getServer().getLevel(key);
        check(level != null, "Missing dimension " + key);
        var d = Planet.of(level);
        int half = d.size() / 2;
        var marker = new BlockPos(5, 90, 5);
        boolean reopened = level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK);
        var canonical = new BlockPos(-half, 80, -half);
        level.setBlock(canonical, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        for (int i = -1; i <= 1; i++)
          for (int j = -1; j <= 1; j++)
            check(
                level
                    .getBlockState(canonical.offset(i * d.size(), 0, j * d.size()))
                    .is(Blocks.GOLD_BLOCK),
                "Canonical reads at diagonal seam");
        check(
            level.getChunk(half / 16, 0) == level.getChunk(-half / 16, 0),
            "Chunk source identity at edge");
        level.setBlock(canonical.offset(d.size(), 0, d.size()), Blocks.AIR.defaultBlockState(), 3);
        check(level.getBlockState(canonical).isAir(), "Mining through alias");
        var chest = canonical.offset(0, 1, 0);
        level.setBlock(chest.offset(d.size(), 0, 0), Blocks.CHEST.defaultBlockState(), 3);
        if (reopened)
          check(
              ((ChestBlockEntity) level.getBlockEntity(chest)).getItem(0).getCount() == 3,
              "Inventory restored from saved chunk");
        var inventory = (ChestBlockEntity) level.getBlockEntity(chest);
        check(inventory != null, "Canonical chest entity");
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));
        check(
            level.getBlockEntity(chest.offset(d.size(), 0, d.size())) == inventory,
            "No duplicated block entity");
        check(
            ((ChestBlockEntity) level.getBlockEntity(chest.offset(0, 0, d.size())))
                    .getItem(0)
                    .getCount()
                == 3,
            "Shared inventory");
        level.setBlock(new BlockPos(-half, 65, 0), Blocks.STONE.defaultBlockState(), 3);
        check(
            !level.noCollision(null, new AABB(half + .1, 65, .1, half + .8, 65.8, .8)),
            "Collision across east edge");
        level.setBlock(new BlockPos(-half, 65, 0), Blocks.AIR.defaultBlockState(), 3);
        var entity = EntityType.PIG.create(level);
        check(entity != null, "Create test entity");
        for (double[] point :
            new double[][] {
              {half + .25, 0},
              {-half - .25, 0},
              {0, half + .25},
              {0, -half - .25},
              {half + .25, half + .25}
            }) {
          entity.setPos(point[0], 70, point[1]);
          entity.setYRot(37);
          entity.setXRot(-12);
          entity.setDeltaMovement(.2, .3, .4);
          PlanetServer.wrap(entity);
          check(
              Math.abs(entity.getX()) < half && Math.abs(entity.getZ()) < half,
              "Entity edge/diagonal wrap");
          check(entity.getYRot() == 37 && entity.getXRot() == -12, "No camera/control rotation");
          check(
              entity.getDeltaMovement().equals(new Vec3(.2, .3, .4)),
              "Horizontal seam preserves velocity");
        }
        var walker = EntityType.PIG.create(level);
        for (int dx = -2; dx <= 2; dx++)
          for (int dz = -2; dz <= 2; dz++) level.getChunk(d.chunk((half - 4) / 16 + dx), dz);
        walker.moveTo(half - 3.5, 65, 20.5, 0, 0);
        level.addFreshEntity(walker);
        walker.setOnGround(true);
        var path = walker.getNavigation().createPath(new BlockPos(-half + 3, 65, 20), 0);
        check(
            path != null && path.canReach() && path.getNodeCount() < 16,
            "Mob path chooses connected edge: " + path);
        var seen = EntityType.PIG.create(level);
        seen.moveTo(-half + 3.5, 65, 20.5, 0, 0);
        level.addFreshEntity(seen);
        check(walker.hasLineOfSight(seen), "Mob sees target through connected edge");
        walker.getMoveControl().setWantedPosition(seen.getX(), seen.getY(), seen.getZ(), 1);
        check(
            Math.abs(walker.getMoveControl().getWantedX() - walker.getX()) < 8,
            "Move control takes nearest target");
        seen.discard();
        walker.discard();
        var boat = EntityType.BOAT.create(level);
        var rider = EntityType.PIG.create(level);
        boat.moveTo(half + 2, 65, 30);
        level.addFreshEntity(boat);
        level.addFreshEntity(rider);
        rider.startRiding(boat, true);
        PlanetServer.wrap(boat);
        check(
            rider.getVehicle() == boat
                && Math.abs(rider.getX() - boat.getX()) < 3
                && boat.getX() < 0,
            "Vehicle and passenger wrap together");
        rider.discard();
        boat.discard();
        for (int axis = 0; axis < 2; axis++)
          for (int sign : new int[] {-1, 1}) {
            entity.setPos(.5, 65, .5);
            for (int step = 0; step < d.size() * 4; step++) {
              entity.move(
                  MoverType.SELF,
                  new Vec3(axis == 0 ? sign * .25 : 0, 0, axis == 1 ? sign * .25 : 0));
              PlanetServer.wrap(entity);
            }
            check(
                Math.abs(entity.getX() - .5) < 1e-7 && Math.abs(entity.getZ() - .5) < 1e-7,
                "Full physical lap " + axis + " sign " + sign);
          }
        // A shaft crosses a seam and reaches below the fallthrough boundary.
        for (int y = d.bottom() - 2; y <= 65; y++)
          for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
              level.setBlock(new BlockPos(half + x, y, z), Blocks.AIR.defaultBlockState(), 3);
        entity.setPos(half - .5, 64, .5);
        entity.move(MoverType.SELF, new Vec3(0, -d.radius() - 1, 0));
        check(entity.getY() < d.bottom(), "Deep shaft collision/mining");
        entity.setDeltaMovement(.1, -.8, .2);
        PlanetServer.wrap(entity);
        check(
            entity.getY() == d.bottom() + 1 && entity.getDeltaMovement().y == .8,
            "Fallthrough position and inverted velocity");
        var settings = PlanetSettings.get(level);
        settings.fallthrough = false;
        entity.setPos(0, d.bottom() - 1, 0);
        PlanetServer.wrap(entity);
        check(entity.getY() == d.bottom() - 1, "Disabled fallthrough");
        settings.fallthrough = true;
        settings.centrifugal = false;
        settings.realisticGravity = false;
        entity.setPos(0, 64, 0);
        entity.setDeltaMovement(Vec3.ZERO);
        double base = entity.getGravity();
        settings.realisticGravity = true;
        entity.setPos(0, 64 + d.projectionRadius(), 0);
        check(Math.abs(entity.getGravity() - base * Math.exp(-2)) < 1e-10, "Altitude gravity");
        settings.centrifugal = true;
        entity.setDeltaMovement(.3, 0, .4);
        check(
            Math.abs(
                    entity.getGravity()
                        - (base * Math.exp(-2) - .5 / (d.projectionRadius() * Math.E)))
                < 1e-10,
            "Final centrifugal formula");
        settings.realisticGravity = false;
        settings.setDirty();
        // TNT/explosion code exercises periodic entity distance, sight rays and knockback.
        var victim = EntityType.PIG.create(level);
        victim.setNoAi(true);
        victim.setPos(-half + .5, 70, 30.5);
        level.addFreshEntity(victim);
        level.explode(
            null,
            half - .5,
            70,
            30.5,
            2f,
            net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        check(
            victim.getHealth() < victim.getMaxHealth(), "TNT damages entity across connected edge");
        victim.discard();
        for (int height : new int[] {80, 120, 180}) {
          level.setBlock(new BlockPos(30, height, 30), Blocks.TNT.defaultBlockState(), 3);
          level.explode(
              null,
              30.5,
              height,
              30.5,
              2f,
              net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        }
        level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        results.add(
            "PASS "
                + key.location()
                + ": canonical chunks, nine aliases, seam writes/mining/inventory, collision, 4"
                + " edges + diagonal, 4 full physical laps, yaw/pitch/velocity, deep shaft, bottom"
                + " on/off, gravity/centrifugal"
                + (reopened ? ", REOPEN persistence" : "; first persistence marker written"));
      }
      var natural = e.getServer().getLevel(SpaceBlocks.NATURAL);
      check(natural != null, "Natural dimension registered");
      var generator = (PlanetGenerator) natural.getChunkSource().getGenerator();
      check(generator.natural, "Natural generator enabled");
      int low = 999, high = -999, caves = 0, ores = 0, water = 0;
      var biomes = new HashSet<String>();
      for (int x = -800; x <= 800; x += 160)
        for (int z = -800; z <= 800; z += 160) {
          var column =
              generator.getBaseColumn(x, z, natural, natural.getChunkSource().randomState());
          int height = generator.surface(x, z, natural.getChunkSource().randomState());
          low = Math.min(low, height);
          high = Math.max(high, height);
          biomes.add(
              generator
                  .getBiomeSource()
                  .getNoiseBiome(
                      x >> 2, 70 >> 2, z >> 2, natural.getChunkSource().randomState().sampler())
                  .unwrapKey()
                  .orElseThrow()
                  .location()
                  .toString());
          for (int y = -490; y < height - 5; y++) {
            var state = column.getBlock(y);
            if (state.isAir()) caves++;
            if (state.getBlock().toString().contains("ore")) ores++;
          }
          if (column.getBlock(64).is(Blocks.WATER) || column.getBlock(64).is(Blocks.ICE)) water++;
          check(column.getBlock(-496).is(Blocks.BEDROCK), "Natural core floor");
          check(
              height
                  == generator.surface(x + 1632, z - 1632, natural.getChunkSource().randomState()),
              "Natural height repeats across seams");
        }
      check(
          high - low > 40 && water > 0 && caves > 0 && ores > 0 && biomes.size() >= 4,
          "Natural map relief, water, caves, ores and biomes");
      var np = new BlockPos(-816, -300, 0);
      natural.setBlock(np, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
      check(
          natural.getBlockState(np.offset(1632, 0, 0)).is(Blocks.DIAMOND_BLOCK),
          "Natural deep seam block");
      var map =
          new PeriodicTerrain(
              1632, PeriodicTerrain.seed(natural.getChunkSource().randomState().sampler()));
      BlockPos generatedChest = null;
      search:
      for (int x = -800; x < 800; x += 8)
        for (int z = -800; z < 800; z += 8)
          for (var column : PeriodicStructures.columns(x, z, 1632, map))
            if (!column.cabin() && Math.abs(column.dx()) <= 3 && Math.abs(column.dz()) <= 3) {
              generatedChest =
                  Planet.NATURAL.canonical(
                      new BlockPos(x - column.dx() + 2, column.floor() + 1, z - column.dz() + 2));
              break search;
            }
      check(generatedChest != null, "Procedural dungeon located");
      natural.getChunk(generatedChest.getX() >> 4, generatedChest.getZ() >> 4);
      check(
          natural.getBlockState(generatedChest).is(Blocks.CHEST),
          "Procedural dungeon chest generated in real chunk");
      var generatedInventory = natural.getBlockEntity(generatedChest);
      check(
          generatedInventory instanceof ChestBlockEntity
              && generatedInventory
                  .saveWithFullMetadata(natural.registryAccess())
                  .getString("LootTable")
                  .equals("minecraft:chests/simple_dungeon"),
          "Generated chest retains vanilla dungeon loot table");
      results.add(
          "PASS natural planet: height range="
              + low
              + ".."
              + high
              + ", biomes="
              + biomes.size()
              + ", cave samples="
              + caves
              + ", ores="
              + ores
              + ", water columns="
              + water
              + ", periodic height/deep storage");
      e.getServer().saveEverything(false, true, true);
      Files.write(Path.of("periodic-server-results.txt"), results);
      for (var line : results) SpaceBlocks.LOGGER.info(line);
      SpaceBlocks.LOGGER.info("PERIODIC_SERVER_TEST_PASS");
    } catch (Throwable ex) {
      SpaceBlocks.LOGGER.error("PERIODIC_SERVER_TEST_FAIL", ex);
      try {
        Files.writeString(Path.of("periodic-server-results.txt"), "FAIL: " + ex);
      } catch (Exception ignored) {
      }
      throw new IllegalStateException("Periodic server checks failed", ex);
    } finally {
      e.getServer().halt(false);
    }
  }
}
