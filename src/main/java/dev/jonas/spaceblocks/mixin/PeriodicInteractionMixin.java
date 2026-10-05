package dev.jonas.spaceblocks.mixin;
import dev.jonas.spaceblocks.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Player.class)
abstract class PeriodicInteractionMixin {
    @ModifyVariable(method="canInteractWithBlock",at=@At("HEAD"),argsOnly=true)
    private BlockPos block(BlockPos pos){var p=(Player)(Object)this;var d=Planet.of(p.level());if(d==null)return pos;var o=PeriodicMath.nearest(pos.getX(),pos.getZ(),p.getX(),p.getZ(),d.size());return pos.offset(o.x(),0,o.z());}
    @ModifyVariable(method="canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z",at=@At("HEAD"),argsOnly=true)
    private AABB entity(AABB box){var p=(Player)(Object)this;var d=Planet.of(p.level());if(d==null)return box;var c=box.getCenter();var delta=d.delta(p.position(),c);return box.move(p.getX()+delta.x-c.x,0,p.getZ()+delta.z-c.z);}
}
