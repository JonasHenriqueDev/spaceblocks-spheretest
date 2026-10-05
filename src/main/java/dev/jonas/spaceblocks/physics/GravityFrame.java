package dev.jonas.spaceblocks.physics;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Parallel-transported tangent frame, with no longitude seam or pole switch. */
public record GravityFrame(Vec3 up, Vec3 north) {
    public static GravityFrame at(Vec3 up) {
        Vec3 reference = Math.abs(up.z) < 0.9 ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        return new GravityFrame(up, tangent(reference, up).normalize());
    }

    public GravityFrame transport(Vec3 newUp) {
        double dot = up.dot(newUp);
        if (dot < -0.9999) return at(newUp);
        Vec3 axis = up.cross(newUp);
        Vec3 rotated = north.add(axis.cross(north)).add(axis.cross(axis.cross(north)).scale(1.0 / (1.0 + dot)));
        return new GravityFrame(newUp, tangent(rotated, newUp).normalize());
    }

    public Vec3 east() { return up.cross(north).normalize(); }
    public Vec3 forward(float yaw) {
        double angle = Math.toRadians(yaw);
        return north.scale(Math.cos(angle)).subtract(east().scale(Math.sin(angle)));
    }
    public Vec3 left(float yaw) {
        double angle = Math.toRadians(yaw);
        return east().scale(Math.cos(angle)).add(north.scale(Math.sin(angle)));
    }
    public Vec3 look(float yaw, float pitch) {
        double angle = Math.toRadians(pitch);
        return forward(yaw).scale(Math.cos(angle)).subtract(up.scale(Math.sin(angle)));
    }
    public Quaternionf rotation() {
        Matrix3f basis = new Matrix3f().setColumn(0, east().toVector3f())
                .setColumn(1, up.toVector3f()).setColumn(2, north.toVector3f());
        return new Quaternionf().setFromNormalized(basis);
    }
    public static Vec3 tangent(Vec3 value, Vec3 up) { return value.subtract(up.scale(value.dot(up))); }
}
