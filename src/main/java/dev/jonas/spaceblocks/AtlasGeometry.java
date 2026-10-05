package dev.jonas.spaceblocks;

/**
 * Geometry for an overview only; gameplay continues to use the original camera-relative renderer.
 */
public final class AtlasGeometry {
  public record Point(double x, double y, double z) {}

  public static Point globe(double u, double v, double radial) {
    double longitude = u * Math.PI * 2, latitude = (v - .5) * Math.PI;
    double ring = Math.cos(latitude) * radial;
    return new Point(
        Math.sin(longitude) * ring, Math.sin(latitude) * radial, Math.cos(longitude) * ring);
  }

  public static Point spheretest(
      double x, double z, double height, double cameraX, double cameraZ, int radius, int size) {
    double dx = PeriodicMath.wrap(x - cameraX, size), dz = PeriodicMath.wrap(z - cameraZ, size);
    var p = PeriodicMath.project(dx, height - Planet.SURFACE, dz, radius);
    return new Point(p.x() / radius, (p.y() + radius) / radius, p.z() / radius);
  }

  public static Point rotate(Point p, double yaw, double pitch) {
    double x = p.x * Math.cos(yaw) + p.z * Math.sin(yaw),
        z = p.z * Math.cos(yaw) - p.x * Math.sin(yaw);
    return new Point(
        x,
        p.y * Math.cos(pitch) - z * Math.sin(pitch),
        z * Math.cos(pitch) + p.y * Math.sin(pitch));
  }
}
