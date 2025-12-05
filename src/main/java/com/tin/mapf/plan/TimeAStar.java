package com.tin.mapf.plan;

import com.tin.mapf.grid.CellKey;
import com.tin.mapf.grid.GridBuilder;
import com.tin.mapf.time.ReservationKey;
import com.tin.mapf.time.ReservationTable;

import java.util.*;

public class TimeAStar {

    /** Simple global counter you can reset/read from CoopPlanner. */
    public static final class Counters {
        private static final java.util.concurrent.atomic.AtomicInteger EXP =
                new java.util.concurrent.atomic.AtomicInteger();
        /** Count one node expansion. Call when polling from OPEN. */
        public static void inc() { EXP.incrementAndGet(); }
        /** Return current count and reset to 0. */
        public static int takeAndReset() { return EXP.getAndSet(0); }
        private Counters() {}
    }

    /** Returns a path as ReservationKeys (cell@tick) up to horizon, or empty if none. */
    public static List<ReservationKey> plan(
            int sx, int sz, int gx, int gz, int t0, int horizon,
            ReservationTable reservations, GridBuilder grid) {

        PriorityQueue<TimeNode> open = new PriorityQueue<>();
        HashMap<TimeNode, Integer> gbest = new HashMap<>();

        TimeNode start = new TimeNode(sx, sz, t0, 0,
                Heuristics.manhattan(sx, sz, gx, gz), null);
        open.add(start);
        gbest.put(start, 0);

        // 4-neighbors + WAIT
        final int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{0,0}};

        while (!open.isEmpty()) {
            TimeNode cur = open.poll();

            // Count one expansion (standard definition)
            Counters.inc();

            // Goal test
            if (cur.x == gx && cur.z == gz) {
                // Reconstruct path (including current state at cur.t)
                List<ReservationKey> out = new ArrayList<>();
                for (TimeNode n = cur; n != null; n = n.parent) {
                    out.add(new ReservationKey(new CellKey(n.x, n.z), n.t));
                }
                Collections.reverse(out);

                // Hold goal for a few ticks (so others respect occupancy)
                final int hold = 10;
                for (int k = 1; k <= hold; k++) {
                    out.add(new ReservationKey(new CellKey(gx, gz), cur.t + k));
                }

                // Trim to horizon
                final int tMax = t0 + horizon;
                out.removeIf(rk -> rk.tick > tMax);
                return out;
            }

            // Stop when horizon reached
            if (cur.t >= t0 + horizon) continue;

            // Explore neighbors
            for (int[] d : dirs) {
                int nx = cur.x + d[0];
                int nz = cur.z + d[1];
                int nt = cur.t + 1;

                // Movement validity: moving requires walkable target; wait is always fine
                if (!(d[0] == 0 && d[1] == 0)) {
                    if (!grid.isWalkable(nx, nz)) continue;
                }

                // Time reservation (vertex) conflict
                if (reservations.isReserved(new CellKey(nx, nz), nt)) continue;

                int ng = cur.g + 1;
                int nh = Heuristics.manhattan(nx, nz, gx, gz);
                TimeNode next = new TimeNode(nx, nz, nt, ng, nh, cur);

                Integer best = gbest.get(next);
                if (best == null || ng < best) {
                    gbest.put(next, ng);
                    open.add(next);
                }
            }
        }

        // No plan within horizon
        return Collections.emptyList();
    }

    private TimeAStar() {}
}
