package com.tin.mapf.plan;

import com.tin.mapf.grid.CellKey;
import com.tin.mapf.grid.GridBuilder;
import com.tin.mapf.time.ReservationKey;
import com.tin.mapf.time.ReservationTable;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

public final class SinglePlanner implements Planner {

    @Override
    public List<ReservationKey> plan(ServerLevel lvl, GridBuilder grid, ReservationTable res,
                                     UUID self, int sx,int sz,int gx,int gz,int nowTick) {
        // Simple 4-neighbor A* on cells (x,z). Cost = 1/step. Heuristic = Manhattan.
        record Node(int x, int z) {}
        record Rec(Node n, int g, int f, Node parent){}

        Node start = new Node(sx, sz);
        Node goal  = new Node(gx, gz);

        PriorityQueue<Rec> open = new PriorityQueue<>(Comparator.comparingInt(r -> r.f));
        Map<Node, Rec> came = new HashMap<>();
        Set<Node> closed = new HashSet<>();

        int h0 = Math.abs(sx - gx) + Math.abs(sz - gz);
        Rec r0 = new Rec(start, 0, h0, null);
        open.add(r0); came.put(start, r0);

        while (!open.isEmpty()) {
            Rec cur = open.poll();
            if (closed.contains(cur.n)) continue;
            closed.add(cur.n);

            if (cur.n.equals(goal)) {
                // Reconstruct cells then attach times linearly from nowTick+1
                List<ReservationKey> out = new ArrayList<>();
                Deque<Node> cells = new ArrayDeque<>();
                for (Rec p = cur; p != null; p = came.get(p.parent)) cells.addFirst(p.n);
                int t = nowTick + 1;
                // skip duplicate first
                Node prev = null;
                for (Node n : cells) {
                    if (prev != null && (n.x != prev.x || n.z != prev.z)) {
                        out.add(new ReservationKey(new CellKey(n.x, n.z), t));
                        t++;
                    }
                    prev = n;
                }
                return out;
            }

            int[][] d4 = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : d4) {
                int nx = cur.n.x + d[0], nz = cur.n.z + d[1];
                if (!grid.isWalkable(nx, nz)) continue;
                Node nn = new Node(nx, nz);
                if (closed.contains(nn)) continue;
                int g = cur.g + 1;
                int f = g + Math.abs(nx - gx) + Math.abs(nz - gz);
                Rec old = came.get(nn);
                if (old == null || g < old.g) {
                    Rec nr = new Rec(nn, g, f, cur.n);
                    came.put(nn, nr);
                    open.add(nr);
                }
            }
        }
        return List.of();
    }
}
