package com.tin.mapf.plan;

import java.util.Objects;

public class TimeNode implements Comparable<TimeNode> {
    public final int x, z, t;
    public final int g, h;
    public final TimeNode parent;

    public TimeNode(int x, int z, int t, int g, int h, TimeNode parent) {
        this.x = x; this.z = z; this.t = t; this.g = g; this.h = h; this.parent = parent;
    }
    public int f() { return g + h; }

    @Override public int compareTo(TimeNode o) { return Integer.compare(this.f(), o.f()); }
    @Override public boolean equals(Object o){
        if(this==o) return true;
        if(!(o instanceof TimeNode)) return false;
        TimeNode n=(TimeNode)o; return x==n.x && z==n.z && t==n.t;
    }
    @Override public int hashCode(){ return Objects.hash(x,z,t); }
}
