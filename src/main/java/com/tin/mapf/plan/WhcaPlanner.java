package com.tin.mapf.plan;

import com.tin.mapf.grid.CellKey;
import com.tin.mapf.grid.GridBuilder;
import com.tin.mapf.time.ReservationKey;
import com.tin.mapf.time.ReservationTable;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

import java.util.List;

public final class WhcaPlanner implements Planner {
    // You can tweak at runtime via /mapf window <W> and /mapf replan <R> later if you want.
    public static int WINDOW = 80;

    @Override
    public List<ReservationKey> plan(ServerLevel lvl, GridBuilder grid, ReservationTable res,
                                     UUID self, int sx,int sz,int gx,int gz,int nowTick) {
        return TimeAStar.plan(sx, sz, gx, gz, nowTick, WINDOW, res, grid);
    }
}
