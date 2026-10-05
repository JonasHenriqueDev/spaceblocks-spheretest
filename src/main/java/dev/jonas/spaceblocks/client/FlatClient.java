package dev.jonas.spaceblocks.client;

import dev.jonas.spaceblocks.SpaceBlocks;
import dev.jonas.spaceblocks.surface.*;
import dev.jonas.spaceblocks.physics.GravityFrame;
import dev.jonas.spaceblocks.network.FlatNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import static dev.jonas.spaceblocks.surface.CubeTopology.*;

public final class FlatClient {
    public static volatile FlatDefinition surface;
    public static GravityFrame frame;
    public static int seams;
    public static boolean active() { var m=Minecraft.getInstance();return m.level!=null&&SpaceBlocks.isFlat(m.level.dimension())&&surface!=null; }
    public static void definition(FlatDefinition d) {
        if(d.faceSize()<32||d.faceSize()>2048||d.guardSize()<32||d.guardSize()>1024)return;
        if(!d.equals(surface)){frame=null;FlatSceneCache.clear();PortalRenderer.clear();surface=d;Minecraft.getInstance().levelRenderer.allChanged();}
    }
    public static void updateFrame(Position p) {
        Vec3 up=sphere(p,surface.faceSize(),1,0);
        if(frame==null) {
            Vec3 east=GravityFrame.tangent(p.face().uAxis,up).normalize();
            frame=new GravityFrame(up,east.cross(up).normalize());
        } else frame=frame.transport(up);
    }
    public static void beforeSend(LocalPlayer player) {
        if(!active()) { frame=null;FlatSceneCache.clear();return; }
        var d=surface;var p=d.locate(player.getX(),player.getZ());if(p==null) return;
        var t=wrap(p,player.getDeltaMovement(),player.getYRot(),d.faceSize());
        updateFrame(canonical(p,d.faceSize()));
        if(t.crossings()==0) return;
        Vec3 old=player.position(),next=d.storage(t.position(),player.getY());
        var ex=wrap(p,new Vec3(1,0,0),0,d.faceSize()).velocity();
        var ez=wrap(p,new Vec3(0,0,1),0,d.faceSize()).velocity();
        float turn=Mth.wrapDegrees(t.yaw()-player.getYRot());
        // Live portal meshes are retained by authoritative source address across chart changes.
        PacketDistributor.sendToServer(new FlatNetwork.Seam(old.x,old.y,old.z,player.getYRot(),player.getXRot()));
        Vec3 previous=new Vec3(player.xo-old.x,player.yo-old.y,player.zo-old.z);
        Vec3 shifted=ex.scale(previous.x).add(0,previous.y,0).add(ez.scale(previous.z));
        player.setPos(next);player.xo=next.x+shifted.x;player.yo=next.y+shifted.y;player.zo=next.z+shifted.z;
        player.xOld=player.xo;player.yOld=player.yo;player.zOld=player.zo;
        player.setDeltaMovement(t.velocity());player.setYRot(player.getYRot()+turn);
        player.yRotO+=turn;player.yBodyRot+=turn;player.yBodyRotO+=turn;player.yHeadRot+=turn;player.yHeadRotO+=turn;
        // Rotate the virtual planet's tangent frame by the inverse chart transport.
        Vec3 oldEast=frame.east(),oldSouth=frame.north();
        Vec3 newSouth=oldEast.scale(ex.z).add(oldSouth.scale(ez.z));
        frame=new GravityFrame(frame.up(),newSouth.normalize());seams++;
        Minecraft.getInstance().level.getChunkSource().updateViewCenter((int)Math.floor(next.x)>>4,(int)Math.floor(next.z)>>4);
        SpaceBlocks.LOGGER.info("FLAT_SEAM_CLIENT: {} -> {} yawTurn={} speed={}",p.face(),t.position().face(),turn,t.velocity().length());
    }
}
