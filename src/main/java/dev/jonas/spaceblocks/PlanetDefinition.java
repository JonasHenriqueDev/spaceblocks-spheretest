package dev.jonas.spaceblocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Body-local coordinates; independent of rendering and Minecraft chunk storage. */
public record PlanetDefinition(double centerX, double centerY, double centerZ, double radius, long seed) {
    public static final Codec<PlanetDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("center_x").forGetter(PlanetDefinition::centerX),
            Codec.DOUBLE.fieldOf("center_y").forGetter(PlanetDefinition::centerY),
            Codec.DOUBLE.fieldOf("center_z").forGetter(PlanetDefinition::centerZ),
            Codec.doubleRange(8, 256).fieldOf("radius").forGetter(PlanetDefinition::radius),
            Codec.LONG.fieldOf("seed").forGetter(PlanetDefinition::seed)
    ).apply(instance, PlanetDefinition::new));

    public double distance(double x, double y, double z) {
        double dx = x - centerX, dy = y - centerY, dz = z - centerZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double surfaceRadius(double x, double y, double z) {
        double dx = x - centerX, dy = y - centerY, dz = z - centerZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.0001) return radius;
        dx /= distance;
        dy /= distance;
        dz /= distance;
        double phase = (seed & 65535L) * 0.001;
        // Continuous 3D sampling on the unit sphere: no longitude seam or polar singularity.
        double broad = Math.sin(dx * 5.0 + phase) * Math.cos(dy * 4.0 - phase)
                + Math.sin(dz * 6.0 + dx * 2.0 + phase);
        double detail = Math.sin(dx * 17.0 + dy * 11.0) * Math.cos(dz * 13.0 - phase);
        // Preserve the original saved planet; new large bodies have gentler relief.
        double broadScale = radius > 128 ? 0.6 : 2.0;
        double detailScale = radius > 128 ? 0.2 : 0.8;
        return radius + broad * broadScale + detail * detailScale;
    }

    public double depth(double x, double y, double z) {
        return surfaceRadius(x, y, z) - distance(x, y, z);
    }
}
