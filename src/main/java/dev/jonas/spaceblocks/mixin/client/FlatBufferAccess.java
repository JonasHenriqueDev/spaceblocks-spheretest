package dev.jonas.spaceblocks.mixin.client;
import com.mojang.blaze3d.vertex.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(VertexBuffer.class)
public interface FlatBufferAccess {
    @Accessor("vertexBufferId") int spaceblocks$vertexId();
    @Accessor("indexCount") int spaceblocks$indexCount();
    @Accessor("mode") VertexFormat.Mode spaceblocks$mode();
}
