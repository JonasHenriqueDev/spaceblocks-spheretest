package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.vertex.*;
import dev.jonas.spaceblocks.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
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
  private record Mesh(BlockPos origin, VertexBuffer[] layers) {}

  private static final LinkedHashMap<BlockPos, Mesh> meshes = new LinkedHashMap<>(256, .75f, true);
  private static final Set<BlockPos> dirty = new HashSet<>();
  private static final List<ChunkPos> visibleChunks = new ArrayList<>();
  private static Object world;
  public static int compiled, drawn;
  public static boolean ownBlockEntities;

  public static void clear() {
    for (var m : meshes.values()) for (var b : m.layers) if (b != null) b.close();
    meshes.clear();
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
    for (var p : meshes.keySet()) if (p.getX() >> 4 == x && p.getZ() >> 4 == z) dirty.add(p);
  }

  public static void frame(RenderFrameEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (world != mc.level) {
      clear();
      world = mc.level;
    }
    if (!PlanetClient.active() || mc.player == null) return;
    var d = PlanetClient.planet();
    var eye = mc.gameRenderer.getMainCamera().getPosition();
    int range = Math.min(mc.options.getEffectiveRenderDistance(), d.size() / 32);
    Set<Long> unique = new HashSet<>();
    visibleChunks.clear();
    var pending = new ArrayList<BlockPos>();
    int cx = (int) Math.floor(eye.x) >> 4, cz = (int) Math.floor(eye.z) >> 4;
    for (int x = -range; x <= range; x++)
      for (int z = -range; z <= range; z++) {
        var p = new ChunkPos(d.chunk(cx + x), d.chunk(cz + z));
        if (!unique.add(p.toLong())) continue;
        var offset =
            PeriodicMath.nearest(p.getMinBlockX(), p.getMinBlockZ(), eye.x, eye.z, d.size());
        if (Math.hypot(p.getMinBlockX() + offset.x() - eye.x, p.getMinBlockZ() + offset.z() - eye.z)
            > d.size() / 4.0) continue;
        var chunk = mc.level.getChunkSource().getChunk(p.x, p.z, ChunkStatus.FULL, false);
        if (chunk == null || chunk instanceof EmptyLevelChunk) continue;
        visibleChunks.add(p);
        for (int i = 0; i < chunk.getSectionsCount(); i++) {
          int y = chunk.getSectionYFromSectionIndex(i) * 16;
          var origin = new BlockPos(p.getMinBlockX(), y, p.getMinBlockZ());
          if (chunk.getSection(i).hasOnlyAir()) {
            var old = meshes.remove(origin);
            if (old != null) for (var b : old.layers) if (b != null) b.close();
            continue;
          }
          if (y + 16 < d.bottom()) continue;
          if (!meshes.containsKey(origin) || dirty.contains(origin)) pending.add(origin);
        }
      }
    pending.sort(
        Comparator.comparingDouble(
            p -> {
              var delta = d.delta(eye, Vec3.atCenterOf(p.offset(8, 8, 8)));
              return delta.lengthSqr();
            }));
    long deadline = System.nanoTime() + 8_000_000;
    for (int i = 0; i < Math.min(4, pending.size()); i++) {
      compile(pending.get(i));
      if (System.nanoTime() > deadline) break;
    }
    while (meshes.size() > 2048) {
      var it = meshes.entrySet().iterator();
      var old = it.next().getValue();
      it.remove();
      for (var b : old.layers) if (b != null) b.close();
    }
  }

  private static void compile(BlockPos origin) {
    var mc = Minecraft.getInstance();
    var chunk =
        mc.level
            .getChunkSource()
            .getChunk(origin.getX() >> 4, origin.getZ() >> 4, ChunkStatus.FULL, false);
    if (chunk == null) return;
    var memory = new ByteBufferBuilder[4];
    var builders = new BufferBuilder[4];
    var result = new VertexBuffer[4];
    try {
      for (int i = 0; i < 4; i++) {
        memory[i] = new ByteBufferBuilder(65536);
        builders[i] =
            new BufferBuilder(memory[i], VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
      }
      var pose = new PoseStack();
      var random = RandomSource.create(0);
      var dispatcher = mc.getBlockRenderer();
      var p = new BlockPos.MutableBlockPos();
      for (int y = 0; y < 16; y++)
        for (int z = 0; z < 16; z++)
          for (int x = 0; x < 16; x++) {
            p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            var state = chunk.getBlockState(p);
            if (state.getRenderShape() == RenderShape.MODEL) {
              random.setSeed(state.getSeed(p));
              var data = chunk.getModelData(p);
              if (data == null) data = ModelData.EMPTY;
              for (var type : dispatcher.getBlockModel(state).getRenderTypes(state, random, data)) {
                int layer = PlanetShaders.layer(type);
                if (layer < 0) continue;
                pose.pushPose();
                pose.translate(x, y, z);
                dispatcher.renderBatched(
                    state, p, mc.level, pose, builders[layer], true, random, data, type);
                pose.popPose();
              }
            }
            var fluid = state.getFluidState();
            if (!fluid.isEmpty()) dispatcher.renderLiquid(p, mc.level, builders[3], state, fluid);
          }
      for (int i = 0; i < 4; i++) {
        var mesh = builders[i].build();
        if (mesh == null) continue;
        result[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
        result[i].bind();
        result[i].upload(mesh);
      }
      VertexBuffer.unbind();
      var old = meshes.put(origin, new Mesh(origin, result));
      if (old != null) for (var b : old.layers) if (b != null) b.close();
      dirty.remove(origin);
      compiled++;
    } finally {
      for (var m : memory) if (m != null) m.close();
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
    if (layer == 0) drawn = 0;
    type.setupRenderState();
    shader.getUniform("PlanetRadius").set((float) d.radius());
    shader.getUniform("Eye").set((float) eye.x, (float) eye.y, (float) eye.z);
    try {
      for (var mesh : meshes.values()) {
        var p = mesh.origin;
        var offset = PeriodicMath.nearest(p.getX(), p.getZ(), eye.x, eye.z, d.size());
        if (Math.hypot(p.getX() + offset.x() - eye.x, p.getZ() + offset.z() - eye.z)
                > Math.min(d.size() / 4.0, mc.options.getEffectiveRenderDistance() * 16.0)
            || p.getY() + 16 < d.bottom()) continue;
        var buffer = mesh.layers[layer];
        if (buffer == null) continue;
        shader.CHUNK_OFFSET.set(
            (float) (p.getX() + offset.x()), (float) p.getY(), (float) (p.getZ() + offset.z()));
        buffer.bind();
        buffer.drawWithShader(view, projection, shader);
        drawn++;
      }
    } finally {
      VertexBuffer.unbind();
      type.clearRenderState();
    }
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
              > d.size() / 4.0) continue;
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
