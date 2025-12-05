package com.tin.mapf.plan;

import com.tin.mapf.grid.CellKey;
import com.tin.mapf.grid.GridBuilder;
import com.tin.mapf.time.ReservationKey;
import com.tin.mapf.time.ReservationTable;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** Minimal CBS: vertex-conflict only, costs=SOC, LL = A* with time & constraints. */
public final class CbsPlanner implements Planner {
    public static volatile int cbsSplitsLast = 0;
    /** A constraint forbidding (x,z) at tick t for a specific agent UUID. */
    record VtxConstraint(UUID who, int x, int z, int t) {}
    record PlanForAgent(UUID who, List<ReservationKey> path) {}
    record NodeC(Set<VtxConstraint> cons, Map<UUID, List<ReservationKey>> paths, int cost) {}

    @Override
    public List<ReservationKey> plan(ServerLevel level, GridBuilder grid, ReservationTable res,
                                     UUID self, int sx, int sz, int gx, int gz, int nowTick) {
        Set<VtxConstraint> base = new HashSet<>();

        var snap = res.snapshot();
        for (var e : snap.entrySet()) {
            int t = e.getKey();
            for (var ce : e.getValue().entrySet()) {
                var cell = ce.getKey();
                var who  = ce.getValue();
                if (!who.equals(self)) {
                    base.add(new VtxConstraint(who, cell.x, cell.z, t));
                }
            }
        }

        List<ReservationKey> path = lowLevel(grid, base, sx, sz, gx, gz, nowTick);
        return path == null ? List.of() : path;
    }

    /* ---------- Low-level time-aware A* under vertex constraints ---------- */
    private static List<ReservationKey> lowLevel(GridBuilder grid, Set<VtxConstraint> cons,
                                                 int sx, int sz, int gx, int gz, int now) {
        record S(int x, int z, int t) {}
        record R(S s, int g, int f, R p) {}

        PriorityQueue<R> pq = new PriorityQueue<>(Comparator.comparingInt(r -> r.f));
        Map<S,R> best = new HashMap<>();

        S start = new S(sx, sz, now);
        R r0 = new R(start, 0, h(sx,sz,gx,gz), null);
        pq.add(r0); best.put(start, r0);

        Set<String> vtx = new HashSet<>();
        for (VtxConstraint c : cons) vtx.add(key(c.x,c.z,c.t));

        int[][] d4 = {{1,0},{-1,0},{0,1},{0,-1},{0,0}}; // include WAIT (0,0)

        while (!pq.isEmpty()) {
            R cur = pq.poll();
            S s = cur.s;

            if (s.x == gx && s.z == gz) {
                // Rebuild as reservations for t+1.. (move each tick)
                List<ReservationKey> out = new ArrayList<>();
                Deque<S> states = new ArrayDeque<>();
                for (R p = cur; p != null; p = p.p) states.addFirst(p.s);
                // skip the first node (time=now)
                boolean first = true;
                for (S st : states) {
                    if (first) { first = false; continue; }
                    out.add(new ReservationKey(new CellKey(st.x, st.z), st.t));
                }
                return out;
            }

            for (int[] d : d4) {
                int nx = s.x + d[0], nz = s.z + d[1];
                int nt = s.t + 1;
                if (!grid.isWalkable(nx, nz)) continue;
                if (vtx.contains(key(nx,nz,nt))) continue; // violates vertex constraint
                S ns = new S(nx, nz, nt);
                int g = cur.g + 1;
                int f = g + h(nx,nz,gx,gz);
                R old = best.get(ns);
                if (old == null || g < old.g) {
                    R nr = new R(ns, g, f, cur);
                    best.put(ns, nr);
                    pq.add(nr);
                }
            }
        }
        return null;
    }

    private static int h(int x,int z,int gx,int gz){ return Math.abs(x-gx)+Math.abs(z-gz); }
    private static String key(int x,int z,int t){ return x+"#"+z+"#"+t; }
}
