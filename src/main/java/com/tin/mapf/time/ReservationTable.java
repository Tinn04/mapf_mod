package com.tin.mapf.time;

import com.tin.mapf.grid.CellKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReservationTable {
    private final Map<ReservationKey, UUID> table = new HashMap<>();

    /** True if (cell, tick) is already reserved by someone. */
    public boolean isReserved(CellKey c, int t) {
        return table.containsKey(new ReservationKey(c, t));
    }

    /** Reserve a sequence of (cell, tick) for an agent. Later calls overwrite older entries. */
    public void reserve(UUID agentId, List<ReservationKey> keys) {
        for (ReservationKey k : keys) {
            table.put(k, agentId);
        }
    }

    /** Drop all reservations strictly before tick t. */
    public void pruneBefore(int t) {
        table.keySet().removeIf(k -> k.tick < t);
    }

    /** Remove every reservation owned by this agent. */
    public void clearFor(UUID id) {
        table.entrySet().removeIf(e -> id.equals(e.getValue()));
    }

    /** Clear all reservations. */
    public void clearAll() {
        table.clear();
    }

    /** Immutable snapshot grouped by tick -> (cell -> who). */
    public Map<Integer, Map<CellKey, UUID>> snapshot() {
        Map<Integer, Map<CellKey, UUID>> snap = new HashMap<>();
        for (Map.Entry<ReservationKey, UUID> e : table.entrySet()) {
            int t = e.getKey().tick;
            CellKey c = e.getKey().cell;
            UUID who = e.getValue();
            snap.computeIfAbsent(t, __ -> new HashMap<>()).put(c, who);
        }
        return snap;
    }

    /** Optional helper: who occupies (cell,t) now (null if free). */
    public UUID occupant(CellKey c, int t) {
        return table.get(new ReservationKey(c, t));
    }
}
