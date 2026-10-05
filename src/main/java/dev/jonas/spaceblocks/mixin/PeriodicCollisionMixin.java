package dev.jonas.spaceblocks.mixin;
import dev.jonas.spaceblocks.*;
import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(EntityGetter.class)
public interface PeriodicCollisionMixin {
    @Inject(method="getEntityCollisions",at=@At("HEAD"),cancellable=true)
    private void collision(Entity source,AABB box,CallbackInfoReturnable<List<VoxelShape>> ci){
        if(!((Object)this instanceof Level level))return;var d=Planet.of(level);if(d==null)return;List<VoxelShape> shapes=new ArrayList<>();
        var center=box.getCenter();for(var e:level.getEntities(source,box.inflate(1e-7),EntitySelector.NO_SPECTATORS))if(source==null?e.canBeCollidedWith():source.canCollideWith(e)){
            var delta=d.delta(center,e.position());var shifted=e.getBoundingBox().move(center.x+delta.x-e.getX(),0,center.z+delta.z-e.getZ());if(shifted.intersects(box))shapes.add(Shapes.create(shifted));
        }ci.setReturnValue(shapes);
    }
}
