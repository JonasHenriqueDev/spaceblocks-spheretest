package dev.jonas.spaceblocks.surface;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

/** The cross is the logical map; storage charts have a guard apron for connected terrain. */
public record FlatDefinition(int faceSize,int guardSize,long seed,boolean coloredFaces,boolean relief) {
    public FlatDefinition(int faceSize,int guardSize,long seed) {this(faceSize,guardSize,seed,false,false);}
    public FlatDefinition(int faceSize,int guardSize,long seed,boolean coloredFaces) {this(faceSize,guardSize,seed,coloredFaces,false);}
    public static final Codec<FlatDefinition> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.intRange(32,2048).fieldOf("face_size").forGetter(FlatDefinition::faceSize),
        Codec.intRange(32,1024).fieldOf("guard_size").forGetter(FlatDefinition::guardSize),
        Codec.LONG.fieldOf("seed").forGetter(FlatDefinition::seed),
        Codec.BOOL.optionalFieldOf("colored_faces",false).forGetter(FlatDefinition::coloredFaces),
        Codec.BOOL.optionalFieldOf("relief",false).forGetter(FlatDefinition::relief)).apply(i,FlatDefinition::new));
    public static int faceColor(Face face) {return switch(face){case FRONT->0x80c71f;case RIGHT->0x304baa;case BACK->0x782caa;case LEFT->0xf1be28;case NORTH->0xdedee0;case SOUTH->0xb52928;};}
    public static String faceName(Face face) {return switch(face){case FRONT->"Frente · verde";case RIGHT->"Direita · azul";case BACK->"Verso · roxo";case LEFT->"Esquerda · amarelo";case NORTH->"Norte · branco";case SOUTH->"Sul · vermelho";};}
    public static net.minecraft.world.level.block.state.BlockState faceBlock(Face face) {return (switch(face){case FRONT->net.minecraft.world.level.block.Blocks.LIME_CONCRETE;case RIGHT->net.minecraft.world.level.block.Blocks.BLUE_CONCRETE;case BACK->net.minecraft.world.level.block.Blocks.PURPLE_CONCRETE;case LEFT->net.minecraft.world.level.block.Blocks.YELLOW_CONCRETE;case NORTH->net.minecraft.world.level.block.Blocks.WHITE_CONCRETE;case SOUTH->net.minecraft.world.level.block.Blocks.RED_CONCRETE;}).defaultBlockState();}
    public int stride() { return faceSize+guardSize*2; }
    public double originX(Face face) { return face.column*stride()+guardSize; }
    public double originZ(Face face) { return face.row*stride()+guardSize; }
    public double radius() { return faceSize*Math.sqrt(6/(4*Math.PI)); }
    public Position locate(double x,double z) {
        int column=(int)Math.floor(x/stride()),row=(int)Math.floor(z/stride());
        for(Face face:Face.values()) if(face.column==column && face.row==row)
            return new Position(face,x-originX(face),z-originZ(face));
        return null;
    }
    public Vec3 storage(Position p,double y) { return new Vec3(originX(p.face())+p.u(),y,originZ(p.face())+p.v()); }
    public BlockPos address(Position p,int y) {
        Position c=canonical(p,faceSize);
        return new BlockPos(c.face().ordinal()*faceSize+(int)Math.floor(c.u()),y,(int)Math.floor(c.v()));
    }
    public Position fromAddress(BlockPos p) {
        return new Position(Face.values()[Math.floorDiv(p.getX(),faceSize)],Math.floorMod(p.getX(),faceSize)+.5,p.getZ()+.5);
    }
    public int height(Position p) {
        if(!relief)return 64;
        Vec3 n=SphereProjection.sample(p,faceSize);
        // Sample one continuous 3-D field, rather than independent noise on each face.
        double phase=(seed%10007)*.001;
        double broad=Math.sin(n.x*5.1+phase)*Math.cos(n.y*4.7-phase)+.65*Math.sin(n.z*6.3+n.x*2.1);
        double hills=Math.sin(n.x*19+n.y*13+phase)*Math.cos(n.z*17-n.y*11);
        return 64+(int)Math.round(18*broad+6*hills);

    }
    public int quarterTurns(Position source) {
        return Math.floorMod(Math.round(wrap(source,Vec3.ZERO,0,faceSize).yaw()/90),4);
    }
}
