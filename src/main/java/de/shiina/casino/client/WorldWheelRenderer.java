package de.shiina.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.shiina.casino.ShiinaCasino;
import de.shiina.casino.block.CasinoBlock;
import de.shiina.casino.block.WheelBlockEntity;
import de.shiina.casino.logic.Roulette;
import de.shiina.casino.logic.Rules;
import de.shiina.casino.logic.WheelAnimation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class WorldWheelRenderer implements BlockEntityRenderer<WheelBlockEntity> {
    private static final ResourceLocation GOLD=ResourceLocation.fromNamespaceAndPath(ShiinaCasino.ID,"textures/block/trim.png");
    private final Font font;
    public WorldWheelRenderer(BlockEntityRendererProvider.Context context) { font=context.getFont(); }
    @Override public boolean shouldRenderOffScreen(WheelBlockEntity wheel) { return true; }
    @Override public int getViewDistance() { return 48; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(WheelBlockEntity wheel) {
        return new net.minecraft.world.phys.AABB(wheel.getBlockPos()).inflate(2,3,2);
    }
    @Override public void render(WheelBlockEntity wheel,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(wheel.getLevel()==null) return;
        var block=(CasinoBlock)wheel.getBlockState().getBlock();
        boolean roulette=block.kind.equals("roulette"), expanded=wheel.getBlockState().getValue(CasinoBlock.EXPANDED);
        int count=roulette?37:12, segment=wheel.result;
        if(roulette && segment>=0) for(int i=0;i<37;i++) if(Roulette.ORDER[i]==segment) { segment=i;break; }
        double angle=WheelAnimation.angle(segment,count,wheel.getLevel().getGameTime()+partial-wheel.startedTick,wheel.spinning?wheel.duration:0);
        pose.pushPose(); pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(-wheel.getBlockState().getValue(CasinoBlock.FACING).toYRot()+180));
        if(roulette && expanded) board(pose,buffers,light);
        double radius=roulette?(expanded?.62:.40):(expanded?1.02:.43);
        pose.translate(roulette && expanded?-.85:0,roulette?.965:expanded?1.65:1.1,roulette?0:-.25);
        if(roulette) pose.mulPose(Axis.XP.rotationDegrees(90));
        VertexConsumer vertices=buffers.getBuffer(RenderType.entitySolid(GOLD));
        // The pointer is fixed; only the disk below it rotates.
        quad(pose,vertices,-.06,radius+.10,.06,radius+.13,-.065,0xffd9b45b,light);
        triangle(pose,vertices,new float[]{-.07F,(float)(radius+.11)},new float[]{.07F,(float)(radius+.11)},new float[]{0,(float)(radius-.04)},-.065F,0xffd9b45b,light,true);
        pose.mulPose(Axis.ZP.rotation((float)angle));
        int[] colors={0xffb93236,0xffe39921,0xff3ba046,0xff239a9f,0xff3266b1,0xff823da5};
        int subdivisions=roulette?3:8;
        for(int sector=0;sector<count;sector++) {
            int number=roulette?Roulette.ORDER[sector]:0;
            int color=roulette?(number==0?0xff2b8754:Roulette.red(number)?0xffae293e:0xff1e2330):colors[sector%6];
            for(int part=0;part<subdivisions;part++) {
                double a=(sector+part/(double)subdivisions)*Math.PI*2/count,b=(sector+(part+1)/(double)subdivisions)*Math.PI*2/count;
                float[] va={(float)(Math.cos(a)*radius),(float)(Math.sin(a)*radius)},vb={(float)(Math.cos(b)*radius),(float)(Math.sin(b)*radius)};
                for(boolean front:new boolean[]{true,false}) triangle(pose,vertices,new float[]{0,0},va,vb,front?-.045F:.045F,color,light,front);
                rim(pose,vertices,a,b,radius,light);
            }
        }
        quad(pose,vertices,-.08,-.08,.08,.08,-.051,0xffd9b45b,light);
        // entitySolid uses the shared QUADS/NEW_ENTITY buffer. Font requests other
        // RenderTypes and may finish it: emit ALL disk geometry before any text.
        // No geometry consumer is retained across a getBuffer/font/model call.
        if(!roulette) for(int sector=0;sector<count;sector++) {
            double direction=(sector+.5)*Math.PI*2/count;
            for(boolean front:new boolean[]{true,false}) {
                pose.pushPose(); pose.translate(Math.cos(direction)*radius*.73,Math.sin(direction)*radius*.73,front?-.048:.048);
                if(front) pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.scale(.025F,-.025F,.025F);
                String label=Rules.WHEEL_SEGMENTS.get(sector)+"\u00d7";
                font.drawInBatch(label,-font.width(label)/2F,-4,0xfff7ecd1,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
                pose.popPose();
            }
        }
        pose.popPose();
    }
    private void board(PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose(); pose.translate(.1,.959,-1.12); pose.mulPose(Axis.XP.rotationDegrees(90));
        VertexConsumer v=buffers.getBuffer(RenderType.entitySolid(GOLD));
        double width=.42,height=.205;
        for(int row=0;row<12;row++) for(int column=0;column<3;column++) {
            int number=row*3+column+1; double x=column*width,y=row*height;
            quad(pose,v,x,y,x+width-.025,y+height-.02,-.008,Roulette.red(number)?0xffae293e:0xff1e2330,light);
        }
        quad(pose,v,0,-.23,1.235,-.025,-.008,0xff2b8754,light);
        // Finish the entire betting grid before text changes the shared RenderType.
        for(int row=0;row<12;row++) for(int column=0;column<3;column++) {
            int number=row*3+column+1; double x=column*width,y=row*height;
            pose.pushPose(); pose.translate(x+width/2,y+height/2,-.012); pose.mulPose(Axis.YP.rotationDegrees(180)); pose.scale(.025F,-.025F,.025F);
            String label=Integer.toString(number); font.drawInBatch(label,-font.width(label)/2F,-4,0xfff7ecd1,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light); pose.popPose();
        }
        pose.pushPose();pose.translate(.62,-.125,-.012);pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(.025F,-.025F,.025F);
        font.drawInBatch("0",-3,-4,0xfff7ecd1,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
        pose.popPose();pose.popPose();
    }
    private static void triangle(PoseStack p,VertexConsumer v,float[] c,float[] a,float[] b,float z,int color,int light,boolean front) {
        vertex(p,v,c[0],c[1],z,color,light,front?-1:1);
        float[] second=front?b:a,third=front?a:b;
        vertex(p,v,second[0],second[1],z,color,light,front?-1:1);
        vertex(p,v,third[0],third[1],z,color,light,front?-1:1);
        vertex(p,v,third[0],third[1],z,color,light,front?-1:1);
    }
    private static void rim(PoseStack p,VertexConsumer v,double a,double b,double radius,int light) {
        for(float z:new float[]{-.046F,.046F}) {
            double inner=radius-.035;
            double[] angles=z<0?new double[]{b,a,a,b}:new double[]{a,b,b,a};
            double[] radii={radius,radius,inner,inner};
            for(int i=0;i<4;i++) vertex(p,v,(float)(Math.cos(angles[i])*radii[i]),(float)(Math.sin(angles[i])*radii[i]),z,0xffd9b45b,light,z<0?-1:1);
        }
        // Rim thickness seals the front and back faces.
        vertex(p,v,(float)(Math.cos(a)*radius),(float)(Math.sin(a)*radius),-.045F,0xffd9b45b,light,-1);
        vertex(p,v,(float)(Math.cos(b)*radius),(float)(Math.sin(b)*radius),-.045F,0xffd9b45b,light,-1);
        vertex(p,v,(float)(Math.cos(b)*radius),(float)(Math.sin(b)*radius),.045F,0xffd9b45b,light,1);
        vertex(p,v,(float)(Math.cos(a)*radius),(float)(Math.sin(a)*radius),.045F,0xffd9b45b,light,1);
    }
    private static void quad(PoseStack p,VertexConsumer v,double x0,double y0,double x1,double y1,double z,int color,int light) {
        vertex(p,v,(float)x0,(float)y0,(float)z,color,light,-1);vertex(p,v,(float)x0,(float)y1,(float)z,color,light,-1);
        vertex(p,v,(float)x1,(float)y1,(float)z,color,light,-1);vertex(p,v,(float)x1,(float)y0,(float)z,color,light,-1);
    }
    private static void vertex(PoseStack p,VertexConsumer v,float x,float y,float z,int color,int light,int normal) {
        v.addVertex(p.last(),x,y,z).setColor(color).setUv(.5F,.5F).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p.last(),0,0,normal);
    }
}
