package com.tin.mapf.plan;

import com.tin.mapf.grid.GridBuilder;
import com.tin.mapf.time.ReservationKey;
import com.tin.mapf.time.ReservationTable;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.UUID;

public interface Planner {
    List<ReservationKey> plan(ServerLevel level, GridBuilder grid, ReservationTable res,
                              UUID self, int sx, int sz, int gx, int gz, int nowTick);
}
