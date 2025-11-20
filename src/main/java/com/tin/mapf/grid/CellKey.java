package com.tin.mapf.grid;

import java.util.Objects;

public class CellKey {
    public final int x, z;
    public CellKey(int x, int z) {
        this.x = x;
        this.z = z;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CellKey)) return false;
        CellKey cellKey = (CellKey) o;
        return x == cellKey.x && z == cellKey.z;
    }
    @Override
    public int hashCode() { return Objects.hash(x, z); }
    @Override 
    public String toString() { return "CellKey{" + "x=" + x + ", z=" + z + '}'; }
}
