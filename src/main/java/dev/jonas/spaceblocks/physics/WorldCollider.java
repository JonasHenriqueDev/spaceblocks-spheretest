package dev.jonas.spaceblocks.physics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public record WorldCollider(Level level, Entity entity) implements RadialPhysics.Collider {
    @Override public List<AABB> boxes(AABB bounds) {
        List<AABB> result = new ArrayList<>();
        for (var shape : level.getBlockCollisions(entity, bounds)) result.addAll(shape.toAabbs());
        return result;
    }
}
