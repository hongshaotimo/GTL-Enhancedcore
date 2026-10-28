package com.gtl.enhancedcore.common.structure;

/** Repeat-aware local cursor; no world access or orientation arithmetic. */
public final class ChamberScanCursor {
    public record Cell(int sourceAisle,int x,int y,int z){}
    private final int[] widths,heights,repeats;
    private int aisle,x,y,z,repeat;
    public ChamberScanCursor(int[] widths,int[] heights,int[] repeats,int firstZ){
        if(widths.length!=heights.length||widths.length!=repeats.length)throw new IllegalArgumentException("dimensions");
        this.widths=widths.clone();this.heights=heights.clone();this.repeats=repeats.clone();z=firstZ;
        for(int i=0;i<widths.length;i++)if(widths[i]<1||heights[i]<1||repeats[i]<0)throw new IllegalArgumentException("range");
    }
    public Cell next(){
        while(aisle<repeats.length&&repeats[aisle]==0)aisle++;
        if(aisle>=repeats.length)return null;
        var result=new Cell(aisle,x,y,z);
        if(++x==widths[aisle]){x=0;if(++y==heights[aisle]){y=0;z++;if(++repeat==repeats[aisle]){repeat=0;aisle++;}}}
        return result;
    }
}
