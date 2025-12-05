package com.tin.mapf.time;

import com.tin.mapf.grid.CellKey;
import java.util.Objects;

public class ReservationKey {
    public final CellKey cell;
    public final int tick;

    public ReservationKey(CellKey cell, int tick) {
        this.cell = cell;
        this.tick = tick;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationKey)) return false;
        ReservationKey k = (ReservationKey) o;
        return tick == k.tick && Objects.equals(cell, k.cell);
    }

    @Override public int hashCode() {
        return Objects.hash(cell, tick);
    }

    @Override public String toString() {
        return cell + "@t=" + tick;
    }
}