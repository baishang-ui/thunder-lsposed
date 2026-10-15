package com.oplus.systemui.qs.base.res.model;
public enum TileViewFlag {
 Active(101),Inactive(102),Inoperable(103),Unavailable(104),Dragged(105);
 private final int attr;TileViewFlag(int attr){this.attr=attr;}public int getAttr(){return attr;}
}
