package de.shiina.casino.block;

import de.shiina.casino.logic.StationLayout;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StationShapes {
    private static final Map<String,VoxelShape> CACHE=new HashMap<>();
    static {
        for (String kind : new String[]{"blackjack","roulette","wheel"}) {
            var parts=StationLayout.parts(kind);
            for (int index=-1;index<parts.size();index++) {
                var part=index<0 ? new StationLayout.Part(0,0,0,-1) : parts.get(index);
                VoxelShape shape;
                if (part.seat()>=0) {
                    shape=Shapes.or(Block.box(3,8,3,13,10,13),Block.box(3,0,3,5,8,5),Block.box(11,0,3,13,8,5),
                            Block.box(3,0,11,5,8,13),Block.box(11,0,11,13,8,13),
                            part.seat()<3 ? Block.box(3,10,2,13,21,4) : Block.box(3,10,12,13,21,14));
                } else if (kind.equals("wheel")) {
                    shape=part.y()==0 ? Block.box(0,0,3,16,4,13) : Shapes.empty();
                    if (part.x()==0) shape=Shapes.or(shape,Block.box(6,part.y()==0?4:0,6,10,16,10));
                    // The upright disk's thickness, not a solid wall, defines its collision.
                    if (part.y()>0) shape=Shapes.or(shape,Block.box(0,0,3,16,16,5));
                } else {
                    shape=Block.box(0,11,0,16,15.1,16);
                    int minZ=kind.equals("roulette")?-1:0;
                    if (Math.abs(part.x())==1 && (part.z()==minZ || part.z()==1)) {
                        int x=part.x()<0?1:12,z=part.z()==minZ?1:12;
                        shape=Shapes.or(shape,Block.box(x,0,z,x+3,11,z+3));
                    }
                }
                for (Direction facing : Direction.Plane.HORIZONTAL) CACHE.put(key(kind,index,facing),rotate(shape,StationStructure.turns(facing)));
            }
        }
    }
    private static String key(String kind,int index,Direction direction) { return kind+":"+index+":"+direction; }
    private static VoxelShape rotate(VoxelShape original,int turns) {
        VoxelShape[] result={Shapes.empty()};
        original.forAllBoxes((x0,y0,z0,x1,y1,z1)-> {
            double a=x0,b=z0,c=x1,d=z1;
            for(int i=0;i<turns;i++) { double na=1-d,nb=a,nc=1-b,nd=c; a=na;b=nb;c=nc;d=nd; }
            result[0]=Shapes.or(result[0],Shapes.box(a,y0,b,c,y1,d));
        });
        return result[0].optimize();
    }
    public static VoxelShape part(String kind,int index,Direction facing) { return CACHE.getOrDefault(key(kind,index,facing),Shapes.empty()); }
    private StationShapes() {}
}
