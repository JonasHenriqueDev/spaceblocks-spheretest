package dev.jonas.spaceblocks.physics;

import dev.jonas.spaceblocks.PlanetDefinition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fixed 20 Hz, shared client prediction and authoritative server simulation. */
public final class RadialPhysics {
    public static final double RADIUS = 0.30;
    public static final double GRAVITY = 0.08;
    private static final double SKIN = 0.00002;
    // A one-block axis step can require sqrt(3) blocks along radial up at a cube diagonal.
    private static final double STEP_HEIGHT = 1.8;

    public interface Collider { List<AABB> boxes(AABB bounds); }
    public record Input(int sequence, float forward, float left, boolean jump, boolean sprint,
                        boolean crouch, float yaw, float pitch) {
        public static final Input IDLE = new Input(-1, 0, 0, false, false, false, 0, 0);
    }
    public record State(Vec3 position, Vec3 velocity, GravityFrame frame, boolean grounded, boolean jumpHeld) {
        public static State at(Vec3 position, PlanetDefinition body) {
            return new State(position, Vec3.ZERO, GravityFrame.at(up(position, body)), false, false);
        }
    }
    public record Contact(Vec3 position, Vec3 normal, boolean hit) {}

    private RadialPhysics() {}

    public static Vec3 up(Vec3 position, PlanetDefinition body) {
        Vec3 radial = position.subtract(new Vec3(body.centerX(), body.centerY(), body.centerZ()));
        return radial.lengthSqr() < 0.00001 ? new Vec3(0, 1, 0) : radial.normalize();
    }

    public static AABB bounds(Vec3 position, Vec3 up, double height) {
        Vec3 low = position.add(up.scale(RADIUS));
        Vec3 high = position.add(up.scale(height - RADIUS));
        return new AABB(Math.min(low.x, high.x) - RADIUS, Math.min(low.y, high.y) - RADIUS,
                Math.min(low.z, high.z) - RADIUS, Math.max(low.x, high.x) + RADIUS,
                Math.max(low.y, high.y) + RADIUS, Math.max(low.z, high.z) + RADIUS);
    }

    public static State tick(State old, Input input, PlanetDefinition body, Collider collider) {
        Vec3 up = up(old.position, body);
        GravityFrame frame = old.frame.transport(up);
        double height = input.crouch ? 1.5 : 1.8;
        Vec3 planar = GravityFrame.tangent(old.velocity, up);
        double radialSpeed = old.velocity.dot(up);
        Vec3 wish = frame.forward(input.yaw).scale(input.forward).add(frame.left(input.yaw).scale(input.left));
        if (wish.lengthSqr() > 1) wish = wish.normalize();
        double speed = input.crouch ? 0.09 : input.sprint ? 0.30 : 0.215;
        if (old.grounded) {
            planar = planar.scale(0.35).add(wish.scale(speed * 0.65));
            radialSpeed = Math.max(radialSpeed, -0.05);
        } else {
            planar = planar.scale(0.98).add(wish.scale(0.025));
            if (planar.lengthSqr() > speed * speed) planar = planar.normalize().scale(speed);
        }
        if (old.grounded && input.jump && !old.jumpHeld) radialSpeed = 0.46;
        radialSpeed = Math.max(-2.0, radialSpeed - GRAVITY);
        Vec3 velocity = planar.add(up.scale(radialSpeed));
        Vec3 position = old.position;
        boolean grounded = false;
        int subdivisions = Math.max(1, (int) Math.ceil(velocity.length() / 0.12));
        Vec3 delta = velocity.scale(1.0 / subdivisions);
        for (int part = 0; part < subdivisions; part++) {
            Vec3 localUp = up(position, body);
            Contact moved = resolve(position.add(delta), localUp, height, collider);
            Vec3 desiredPlanar = GravityFrame.tangent(delta, localUp);
            double progress = desiredPlanar.lengthSqr() < 1.0e-9 ? 1
                    : moved.position.subtract(position).dot(desiredPlanar) / desiredPlanar.lengthSqr();
            if ((old.grounded || grounded) && radialSpeed <= 0 && progress < 0.65 && desiredPlanar.lengthSqr() > 0.00001) {
                Contact stepped = step(position, desiredPlanar, localUp, height, collider);
                if (stepped != null) moved = stepped;
            }
            position = moved.position;
            if (moved.hit && moved.normal.dot(localUp) > 0.05 && radialSpeed <= 0) grounded = true;
            if (moved.hit && velocity.dot(moved.normal) < 0) velocity = velocity.subtract(moved.normal.scale(velocity.dot(moved.normal)));
        }
        Vec3 finalUp = up(position, body);
        // Small support probe follows descending voxel steps, without sticking during a jump.
        if (radialSpeed <= 0) {
            Contact support = resolve(position.subtract(finalUp.scale(0.12)), finalUp, height, collider);
            if (support.hit && support.normal.dot(finalUp) > 0.05) {
                position = support.position;
                grounded = true;
            }
        }
        if (grounded) velocity = GravityFrame.tangent(velocity, finalUp);
        return new State(position, velocity, frame.transport(up(position, body)), grounded, input.jump);
    }

    static Contact step(Vec3 position, Vec3 planar, Vec3 up, double height, Collider collider) {
        for (double lift = 0.2; lift <= STEP_HEIGHT + 0.01; lift += 0.1) {
            Vec3 raised = position.add(up.scale(lift));
            Contact clearance = resolve(raised, up, height, collider);
            if (clearance.position.distanceToSqr(raised) > 0.0001) continue;
            Vec3 target = raised.add(planar);
            Contact across = resolve(target, up, height, collider);
            if (across.position.distanceToSqr(target) > 0.0001) continue;
            Vec3 down = across.position;
            for (double drop = 0; drop < lift + 0.2; drop += 0.08) {
                Contact contact = resolve(down.subtract(up.scale(0.08)), up, height, collider);
                if (contact.hit && contact.normal.dot(up) > 0.05) return contact;
                if (contact.hit) break;
                down = contact.position;
            }
        }
        return null;
    }

    /** Overlapping spheres form a capsule approximation; collisions use real voxel shapes. */
    public static Contact resolve(Vec3 original, Vec3 up, double height, Collider collider) {
        Vec3 position = original;
        Vec3 normal = Vec3.ZERO;
        boolean hit = false;
        List<AABB> boxes = collider.boxes(bounds(original, up, height).inflate(0.4));
        int samples = Math.max(3, (int) Math.ceil((height - 2 * RADIUS) / 0.25) + 1);
        for (int iteration = 0; iteration < 6; iteration++) {
            boolean changed = false;
            for (int index = 0; index < samples; index++) {
                double offset = RADIUS + (height - 2 * RADIUS) * index / (samples - 1);
                for (AABB box : boxes) {
                    Vec3 sphere = position.add(up.scale(offset));
                    double closestX = Math.max(box.minX, Math.min(box.maxX, sphere.x));
                    double closestY = Math.max(box.minY, Math.min(box.maxY, sphere.y));
                    double closestZ = Math.max(box.minZ, Math.min(box.maxZ, sphere.z));
                    Vec3 separation = sphere.subtract(closestX, closestY, closestZ);
                    double distanceSquared = separation.lengthSqr();
                    if (distanceSquared >= RADIUS * RADIUS) continue;
                    Vec3 push;
                    if (distanceSquared > 1.0e-12) {
                        double distance = Math.sqrt(distanceSquared);
                        push = separation.scale((RADIUS - distance + SKIN) / distance);
                    } else {
                        double[] depths = {sphere.x - box.minX, box.maxX - sphere.x, sphere.y - box.minY,
                                box.maxY - sphere.y, sphere.z - box.minZ, box.maxZ - sphere.z};
                        int closest = 0;
                        for (int face = 1; face < 6; face++) if (depths[face] < depths[closest]) closest = face;
                        Vec3[] normals = {new Vec3(-1,0,0), new Vec3(1,0,0), new Vec3(0,-1,0),
                                new Vec3(0,1,0), new Vec3(0,0,-1), new Vec3(0,0,1)};
                        push = normals[closest].scale(depths[closest] + RADIUS + SKIN);
                    }
                    Vec3 contactNormal = push.normalize();
                    double floorAlignment = contactNormal.dot(up);
                    // Resolve walkable slopes along radial up, preserving tangent movement.
                    // Ordinary normal-only resolution can push back an entire step during the support probe.
                    if (floorAlignment > 0.35) push = up.scale(push.length() / floorAlignment);
                    position = position.add(push);
                    normal = normal.add(contactNormal);
                    changed = hit = true;
                }
            }
            if (!changed) break;
        }
        return new Contact(position, normal.normalize(), hit);
    }
}
