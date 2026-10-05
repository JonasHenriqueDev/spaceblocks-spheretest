package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * One flat mesh per canonical section; nearest of nine translations at draw time. No virtual
 * cameras, portal recursion, stencil, cube faces or global sphere mesh.
 */
public final class PeriodicRenderer {
  private static final class Mesh {
    final BlockPos origin;
    final VertexBuffer[] layers;
    final MeshData.SortState translucent;
    Vec3 sortEye;
    boolean sortMirrored;

    Mesh(BlockPos origin, VertexBuffer[] layers, MeshData.SortState translucent) {
      this.origin = origin;
      this.layers = layers;
      this.translucent = translucent;
    }
  }

  private static final LinkedHashMap<BlockPos, Mesh> meshes = new LinkedHashMap<>(256, .75f, true);
  private static final net.minecraft.core.Direction[] faces = net.minecraft.core.Direction.values();
  private static final ExecutorService workers =
      Executors.newFixedThreadPool(
          2,
          task -> {
            var thread = new Thread(task, "SpaceBlocks-mesh");
            thread.setDaemon(true);
            return thread;
          });

  private record Job(long epoch) {}

  private record Built(MeshData[] data, ByteBufferBuilder[] memory, MeshData.SortState translucent)
      implements AutoCloseable {
    public void close() {
      for (var mesh : data) if (mesh != null) mesh.close();
      for (var buffer : memory) if (buffer != null) buffer.close();
    }
  }

  private static final Map<BlockPos, Job> jobs = new HashMap<>();
  private static final Set<BlockPos> visibleSections = new HashSet<>();
  private static final Set<BlockPos> normalSections = new HashSet<>(),
      bottomSections = new HashSet<>();
  private static long epoch;
  private static int inFlight;
  public static int pendingSections;

  private static final Set<BlockPos> dirty = new HashSet<>();
  private static final List<ChunkPos> visibleChunks = new ArrayList<>();
  private static Object world;
  private static boolean lastFallthrough;
  public static int compiled, drawn, bottomDrawn;
  public static boolean ownBlockEntities;

  public static void clear() {
    for (var m : meshes.values()) for (var b : m.layers) if (b != null) b.close();
    meshes.clear();
    epoch++;
    jobs.clear();
    visibleSections.clear();
    normalSections.clear();
    bottomSections.clear();
    dirty.clear();
    visibleChunks.clear();
    compiled = drawn = 0;
  }

  public static void changed(int x, int y, int z) {
    var d = PlanetClient.planet();
    if (d == null) return;
    dirty.add(new BlockPos(d.chunk(x) * 16, y * 16, d.chunk(z) * 16));
  }

  public static void chunkChanged(int x, int z) {
    var d = PlanetClient.planet();
    if (d == null) return;
    // Neighbor arrival changes boundary faces and AO, including sections being built.
    for (var p : meshes.keySet())
      if (Math.abs(PeriodicMath.wrap((p.getX() >> 4) - x, d.size() / 16)) <= 1
          && Math.abs(PeriodicMath.wrap((p.getZ() >> 4) - z, d.size() / 16)) <= 1) dirty.add(p);
    for (var p : jobs.keySet())
      if (Math.abs(PeriodicMath.wrap((p.getX() >> 4) - x, d.size() / 16)) <= 1
          && Math.abs(PeriodicMath.wrap((p.getZ() >> 4) - z, d.size() / 16)) <= 1) dirty.add(p);
  }

  public static void frame(RenderFrameEvent.Post event) {
    var mc = Minecraft.getInstance();
    PlanetClient.audio =
        PlanetClient.active()
            ? new PlanetClient.Audio(
                PlanetClient.planet(), mc.gameRenderer.getMainCamera().getPosition())
            : null;
    if (world != mc.level) {
      clear();
      world = mc.level;
    }
    if (!PlanetClient.active() || mc.player == null) return;
    if (lastFallthrough != SpaceBlocks.clientSettings.fallthrough) {
      lastFallthrough = SpaceBlocks.clientSettings.fallthrough;
      dirty.addAll(meshes.keySet());
      dirty.addAll(jobs.keySet());
    }
    var d = PlanetClient.planet();
    var eye = mc.gameRenderer.getMainCamera().getPosition();
    int range = Math.min(mc.options.getEffectiveRenderDistance(), d.size() / 32);
    Set<Long> unique = new HashSet<>();
    visibleChunks.clear();
    visibleSections.clear();
    normalSections.clear();
    bottomSections.clear();
    var pending = new ArrayList<BlockPos>();
    var surfaceSections = new HashSet<BlockPos>();
    boolean aboveSurface =
        eye.y
            >= mc.level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(eye.x),
                    (int) Math.floor(eye.z))
                - 16;
    boolean bottomView = BottomPassage.visible(mc.level, eye.y);
    for (int pass = 0; pass < (bottomView ? 2 : 1); pass++) {
      boolean mirrored = pass == 1;
      var viewEye = mirrored ? BottomPassage.reflect(d, eye) : eye;
      int cx = (int) Math.floor(viewEye.x) >> 4, cz = (int) Math.floor(viewEye.z) >> 4;
      unique.clear();
      for (int x = -range; x <= range; x++)
        for (int z = -range; z <= range; z++) {
          var p = new ChunkPos(d.chunk(cx + x), d.chunk(cz + z));
          if (!unique.add(p.toLong())) continue;
          var offset =
              PeriodicMath.nearest(
                  p.getMinBlockX(), p.getMinBlockZ(), viewEye.x, viewEye.z, d.size());
          if (Math.hypot(
                  p.getMinBlockX() + offset.x() - viewEye.x,
                  p.getMinBlockZ() + offset.z() - viewEye.z)
              > Math.min(d.size() / 4.0, range * 16.0 + 16)) continue;
          var chunk = mc.level.getChunkSource().getChunk(p.x, p.z, ChunkStatus.FULL, false);
          if (chunk == null || chunk instanceof EmptyLevelChunk) continue;
          if (!mirrored) visibleChunks.add(p);
          for (int i = 0; i < chunk.getSectionsCount(); i++) {
            int y = chunk.getSectionYFromSectionIndex(i) * 16;
            var origin = new BlockPos(p.getMinBlockX(), y, p.getMinBlockZ());
            if (chunk.getSection(i).hasOnlyAir()) {
              var old = meshes.remove(origin);
              if (old != null) for (var b : old.layers) if (b != null) b.close();
              continue;
            }
            if (SpaceBlocks.clientSettings.fallthrough && y + 16 < d.bottom()) continue;
            // Match the normal vertical view budget instead of compiling the entire 1536-block
            // column.
            double ground =
                chunk.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8)
                    + 16;
            double anchor = Math.min(viewEye.y, ground);
            // High cameras must keep the ground, even when it is farther below than the view
            // budget.
            // Underground, the same window follows the camera rather than the distant surface.
            if (y + 16 < anchor - range * 16.0 || y > viewEye.y + range * 16.0 + 16) continue;
            (mirrored ? bottomSections : normalSections).add(origin);
            boolean first = visibleSections.add(origin);
            if (!mirrored
                && aboveSurface
                && y + 16
                    >= chunk.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8)
                        - 32) surfaceSections.add(origin);
            if (first
                && (!meshes.containsKey(origin) || dirty.contains(origin))
                && !jobs.containsKey(origin)) pending.add(origin);
          }
        }
    }
    pending.sort(
        Comparator.<BlockPos>comparingInt(p -> surfaceSections.contains(p) ? 0 : 1)
            .thenComparingDouble(
                p -> {
                  var center = new Vec3(p.getX() + 8.5, p.getY() + 8.5, p.getZ() + 8.5);
                  return BottomPassage.nearest(mc.level, eye, center).distanceToSqr(eye);
                }));
    pendingSections = pending.size() + jobs.size();
    var regions = new RenderRegionCache();
    long deadline = System.nanoTime() + 2_000_000;
    for (var origin : pending) {
      if (inFlight >= 4) break;
      var region = regions.createRegion(mc.level, SectionPos.of(origin));
      if (region == null) continue;
      var job = new Job(epoch);
      jobs.put(origin, job);
      dirty.remove(origin);
      inFlight++;
      CompletableFuture.supplyAsync(() -> compile(origin, region), workers)
          .whenComplete(
              (built, error) ->
                  mc.execute(
                      () -> {
                        inFlight--;
                        jobs.remove(origin, job);
                        if (error != null) {
                          dirty.add(origin);
                          SpaceBlocks.LOGGER.error(
                              "Periodic section meshing failed at {}", origin, error);
                          return;
                        }
                        try {
                          if (job.epoch == epoch && visibleSections.contains(origin))
                            upload(origin, built);
                        } finally {
                          built.close();
                        }
                      }));
      if (System.nanoTime() > deadline) break;
    }
    // Never evict a wanted section to admit another wanted section. The old fixed 2048 limit
    // caused perpetual rebuilds and missing terrain with large natural-world view distances.
    for (var iterator = meshes.entrySet().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      if (!visibleSections.contains(entry.getKey())) {
        for (var buffer : entry.getValue().layers) if (buffer != null) buffer.close();
        dirty.remove(entry.getKey());
        iterator.remove();
      }
    }
  }

  public static boolean ready(BlockPos origin) {
    return meshes.containsKey(origin);
  }

  public static int cachedSections() {
    return meshes.size();
  }

  private static void upload(BlockPos origin, Built built) {
    var result = new VertexBuffer[4];
    for (int i = 0; i < 4; i++) {
      var data = built.data[i];
      if (data == null) continue;
      result[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
      result[i].bind();
      result[i].upload(data);
      built.data[i] = null; // upload owns/closes MeshData
    }
    VertexBuffer.unbind();
    var old = meshes.put(origin, new Mesh(origin, result, built.translucent));
    if (old != null) for (var buffer : old.layers) if (buffer != null) buffer.close();
    compiled++;
  }

  private static Built compile(BlockPos origin, RenderChunkRegion region) {
    var mc = Minecraft.getInstance();
    var memory = new ByteBufferBuilder[4];
    var builders = new BufferBuilder[4];
    var dataMeshes = new MeshData[4];
    MeshData.SortState translucent = null;
    boolean success = false;
    try {
      for (int i = 0; i < 4; i++) {
        memory[i] = new ByteBufferBuilder(65536);
        builders[i] =
            new BufferBuilder(memory[i], VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
      }
      var pose = new PoseStack();
      var random = RandomSource.create(0);
      var dispatcher = mc.getBlockRenderer();
      var planet = PlanetClient.planet();
      boolean fallthrough = SpaceBlocks.clientSettings.fallthrough;
      var p = new BlockPos.MutableBlockPos();
      var neighbor = new BlockPos.MutableBlockPos();
      net.minecraft.client.renderer.block.ModelBlockRenderer.enableCaching();
      for (int y = 0; y < 16; y++)
        for (int z = 0; z < 16; z++)
          for (int x = 0; x < 16; x++) {
            p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            var state = region.getBlockState(p);
            boolean bottomFace = planet != null && fallthrough && p.getY() == planet.bottom();
            // A fully enclosed opaque cube has no emitted faces. Avoid model/layer/AO work.
            if (!bottomFace && state.getFluidState().isEmpty() && state.isSolidRender(region, p)) {
              boolean enclosed = true;
              for (var face : faces) {
                neighbor.setWithOffset(p, face);
                if (!region.getBlockState(neighbor).isSolidRender(region, neighbor)) {
                  enclosed = false;
                  break;
                }
              }
              if (enclosed) continue;
            }
            if (state.getRenderShape() == RenderShape.MODEL) {
              random.setSeed(state.getSeed(p));
              var data = region.getModelData(p);
              if (data == null) data = ModelData.EMPTY;
              for (var type : dispatcher.getBlockModel(state).getRenderTypes(state, random, data)) {
                int layer = PlanetShaders.layer(type);
                if (layer < 0) continue;
                pose.pushPose();
                pose.translate(x, y, z);
                dispatcher.renderBatched(
                    state, p, region, pose, builders[layer], true, random, data, type);
                pose.popPose();
              }
            }
            var fluid = state.getFluidState();
            if (!fluid.isEmpty()) dispatcher.renderLiquid(p, region, builders[3], state, fluid);
          }
      for (int i = 0; i < 4; i++) {
        var mesh = builders[i].build();
        if (mesh == null) continue;
        if (i == 3) translucent = mesh.sortQuads(memory[i], VertexSorting.DISTANCE_TO_ORIGIN);
        dataMeshes[i] = mesh;
      }
      success = true;
      return new Built(dataMeshes, memory, translucent);
    } finally {
      net.minecraft.client.renderer.block.ModelBlockRenderer.clearCache();
      if (!success) {
        for (var mesh : dataMeshes) if (mesh != null) mesh.close();
        for (var m : memory) if (m != null) m.close();
      }
    }
  }

  public static void render(RenderType type, Matrix4f view, Matrix4f projection) {
    int layer = PlanetShaders.layer(type);
    if (layer < 0) return;
    var shader = PlanetShaders.shader(layer);
    if (shader == null) return;
    var mc = Minecraft.getInstance();
    var d = PlanetClient.planet();
    var eye = mc.gameRenderer.getMainCamera().getPosition();
    if (layer == 0) {
      drawn = 0;
      bottomDrawn = 0;
    }
    type.setupRenderState();
    shader.getUniform("PlanetRadius").set((float) d.radius());
    shader.getUniform("Eye").set((float) eye.x, (float) eye.y, (float) eye.z);
    shader.getUniform("BottomY").set((float) d.bottom());
    int localLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(eye));
    shader
        .getUniform("BottomLight")
        .set((float) (localLight & 0xffff), (float) ((localLight >>> 16) & 0xffff));
    shader
        .getUniform("SourceFloor")
        .set(SpaceBlocks.clientSettings.fallthrough ? (float) d.bottom() : -100000f);
    try {
      boolean bottomView = BottomPassage.visible(mc.level, eye.y);
      for (int pass = 0; pass < (bottomView ? 2 : 1); pass++) {
        boolean mirrored = pass == 1;
        var viewEye = mirrored ? BottomPassage.reflect(d, eye) : eye;
        var sections = mirrored ? bottomSections : normalSections;
        shader.getUniform("BottomPass").set(mirrored ? 1f : 0f);
        org.lwjgl.opengl.GL11.glFrontFace(
            mirrored ? org.lwjgl.opengl.GL11.GL_CW : org.lwjgl.opengl.GL11.GL_CCW);
        var ordered = new ArrayList<Mesh>();
        for (var mesh : meshes.values())
          if (mesh.layers[layer] != null && sections.contains(mesh.origin)) ordered.add(mesh);
        if (layer == 3)
          ordered.sort(
              Comparator.comparingDouble(
                  (Mesh m) ->
                      -projectedDistance(
                          d,
                          viewEye,
                          m.origin.getX() + 8,
                          m.origin.getY() + 8,
                          m.origin.getZ() + 8,
                          mirrored)));
        for (var mesh : ordered) {
          var p = mesh.origin;
          if (!visibleSections.contains(p)) continue;
          var offset = PeriodicMath.nearest(p.getX(), p.getZ(), viewEye.x, viewEye.z, d.size());
          if (Math.hypot(p.getX() + offset.x() - viewEye.x, p.getZ() + offset.z() - viewEye.z)
                  > Math.min(d.size() / 4.0, mc.options.getEffectiveRenderDistance() * 16.0)
              || SpaceBlocks.clientSettings.fallthrough && p.getY() + 16 < d.bottom()) continue;
          var buffer = mesh.layers[layer];
          if (buffer == null) continue;
          shader.CHUNK_OFFSET.set(
              (float) (p.getX() + offset.x() + (mirrored ? eye.x - viewEye.x : 0)),
              (float) p.getY(),
              (float) (p.getZ() + offset.z()));
          buffer.bind();
          if (layer == 3
              && mesh.translucent != null
              && (mesh.sortEye == null
                  || mesh.sortMirrored != mirrored
                  || d.delta(viewEye, mesh.sortEye).lengthSqr() > 1)) {
            try (var memory = new ByteBufferBuilder(32768)) {
              final double ox = p.getX() + offset.x(), oz = p.getZ() + offset.z();
              var sorted =
                  mesh.translucent.buildSortedIndexBuffer(
                      memory,
                      VertexSorting.byDistance(
                          v ->
                              (float)
                                  projectedDistance(
                                      d, viewEye, ox + v.x, p.getY() + v.y, oz + v.z, mirrored)));
              if (sorted != null) buffer.uploadIndexBuffer(sorted);
            }
            mesh.sortEye = viewEye;
            mesh.sortMirrored = mirrored;
          }
          buffer.drawWithShader(view, projection, shader);
          drawn++;
          if (mirrored) bottomDrawn++;
        }
      }
    } finally {
      org.lwjgl.opengl.GL11.glFrontFace(org.lwjgl.opengl.GL11.GL_CCW);
      shader.getUniform("BottomPass").set(0f);
      VertexBuffer.unbind();
      type.clearRenderState();
    }
  }

  private static double projectedDistance(
      Planet d, Vec3 eye, double x, double y, double z, boolean mirrored) {
    var flat = d.delta(eye, new Vec3(x, y, z));
    var q = PeriodicMath.project(flat.x, mirrored ? -flat.y : flat.y, flat.z, d.radius());
    return q.x() * q.x() + q.y() * q.y() + q.z() * q.z();
  }

  @SuppressWarnings("unchecked")
  public static void stage(RenderLevelStageEvent e) {
    if (!PlanetClient.active() || e.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)
      return;
    var mc = Minecraft.getInstance();
    var eye = e.getCamera().getPosition();
    var d = PlanetClient.planet();
    var pose = e.getPoseStack();
    var buffers = mc.renderBuffers().bufferSource();
    ownBlockEntities = true;
    try {
      for (var cp : visibleChunks) {
        var chunk = mc.level.getChunkSource().getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
        if (chunk == null) continue;
        for (var be : chunk.getBlockEntities().values()) {
          var pos = be.getBlockPos();
          var offset = PeriodicMath.nearest(pos.getX(), pos.getZ(), eye.x, eye.z, d.size());
          if (Math.hypot(pos.getX() + offset.x() - eye.x, pos.getZ() + offset.z() - eye.z)
              > Math.min(d.size() / 4.0, mc.options.getEffectiveRenderDistance() * 16.0 + 16))
            continue;
          var renderer =
              (BlockEntityRenderer<BlockEntity>)
                  mc.getBlockEntityRenderDispatcher().getRenderer(be);
          if (renderer == null) continue;
          pose.pushPose();
          pose.translate(
              pos.getX() + offset.x() - eye.x, pos.getY() - eye.y, pos.getZ() + offset.z() - eye.z);
          MultiBufferSource projected =
              type -> new ProjectedConsumer(buffers.getBuffer(type), new Matrix4f(), d.radius());
          renderer.render(
              be,
              e.getPartialTick().getGameTimeDeltaPartialTick(false),
              pose,
              projected,
              LevelRenderer.getLightColor(mc.level, pos),
              net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
          pose.popPose();
        }
      }
    } finally {
      ownBlockEntities = false;
    }
    buffers.endBatch();
  }
}
