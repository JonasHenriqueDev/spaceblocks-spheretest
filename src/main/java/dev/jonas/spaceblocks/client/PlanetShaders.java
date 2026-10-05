package dev.jonas.spaceblocks.client;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.jonas.spaceblocks.SpaceBlocks;
import java.io.IOException;
import net.minecraft.client.renderer.*;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
public final class PlanetShaders {
    private static final ShaderInstance[] shaders=new ShaderInstance[4];
    public static final RenderType[] TYPES={RenderType.solid(),RenderType.cutoutMipped(),RenderType.cutout(),RenderType.translucent()};
    public static void register(RegisterShadersEvent e)throws IOException{String[] names={"periodic_solid","periodic_mipped","periodic_cutout","periodic_translucent"};for(int i=0;i<4;i++){final int index=i;e.registerShader(new ShaderInstance(e.getResourceProvider(),SpaceBlocks.id(names[i]),DefaultVertexFormat.BLOCK),s->shaders[index]=s);}}
    public static int layer(RenderType type){for(int i=0;i<4;i++)if(TYPES[i]==type)return i;return -1;}
    public static ShaderInstance shader(int layer){return shaders[layer];}
}
