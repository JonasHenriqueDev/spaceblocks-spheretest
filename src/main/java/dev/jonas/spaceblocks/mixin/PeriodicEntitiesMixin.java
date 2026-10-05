package dev.jonas.spaceblocks.mixin;
import dev.jonas.spaceblocks.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Level.class)
abstract class PeriodicEntitiesMixin {
    @Shadow protected abstract LevelEntityGetter<Entity> getEntities();
    @Inject(method="getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",at=@At("HEAD"),cancellable=true)
    private void acrossEdges(Entity excluded,AABB box,Predicate<? super Entity> predicate,CallbackInfoReturnable<List<Entity>> ci){
        var d=Planet.of((Level)(Object)this);if(d==null)return;Set<Entity> found=new LinkedHashSet<>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)getEntities().get(box.move(x*d.size(),0,z*d.size()),e->{if(e!=excluded&&predicate.test(e))found.add(e);});ci.setReturnValue(new ArrayList<>(found));
    }
}
