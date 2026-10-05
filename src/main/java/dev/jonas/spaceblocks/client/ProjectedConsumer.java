package dev.jonas.spaceblocks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jonas.spaceblocks.PeriodicMath;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Projects entity vertices after their model pose, before the view rotation. The first-person hand
 * and inventory never use this wrapper (Spheretest wielded_hand_shader).
 */
public final class ProjectedConsumer implements VertexConsumer {
  private final VertexConsumer target;
  private final Matrix4f inverseView, view;
  private final int radius;

  public ProjectedConsumer(VertexConsumer target, Matrix4f view, int radius) {
    this.target = target;
    this.view = new Matrix4f(view);
    this.inverseView = new Matrix4f(view).invert();
    this.radius = radius;
  }

  @Override
  public VertexConsumer addVertex(float x, float y, float z) {
    var flat = inverseView.transformPosition(new Vector3f(x, y, z));
    var p = PeriodicMath.project(flat.x, flat.y, flat.z, radius);
    var transformed =
        view.transformPosition(new Vector3f((float) p.x(), (float) p.y(), (float) p.z()));
    target.addVertex(transformed.x, transformed.y, transformed.z);
    return this;
  }

  @Override
  public VertexConsumer setColor(int r, int g, int b, int a) {
    target.setColor(r, g, b, a);
    return this;
  }

  @Override
  public VertexConsumer setUv(float u, float v) {
    target.setUv(u, v);
    return this;
  }

  @Override
  public VertexConsumer setUv1(int u, int v) {
    target.setUv1(u, v);
    return this;
  }

  @Override
  public VertexConsumer setUv2(int u, int v) {
    target.setUv2(u, v);
    return this;
  }

  @Override
  public VertexConsumer setNormal(float x, float y, float z) {
    target.setNormal(x, y, z);
    return this;
  }
}
