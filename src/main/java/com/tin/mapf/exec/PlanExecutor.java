package com.tin.mapf.exec;

import com.tin.mapf.grid.CellKey;
import com.tin.mapf.plan.CoopPlanner;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

public final class PlanExecutor {
    public static void tickFollow(Mob mob, int now) {
        var nextOpt = CoopPlanner.cellAt(mob.getUUID(), now + 1);
        if (nextOpt.isEmpty()) return;

        int y = CoopPlanner.arenaFeetY(); // feet layer of the arena
        var c = nextOpt.get();
        double tx = c.x + 0.5, tz = c.z + 0.5;

        // horizontal distance to the center of the reserved cell
        double dx = tx - mob.getX();
        double dz = tz - mob.getZ();
        double d2 = dx*dx + dz*dz;

        // Far: let vanilla pathfinding walk there
        if (d2 > 0.75 * 0.75) {
            mob.getNavigation().moveTo(tx, y, tz, 1.0);
            return;
        }

        // Near: stop the navigator and gently nudge with move control
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(tx, y, tz, 0.35);

        // Only when *extremely* close do we snap to perfect center
        if (d2 < 0.06 * 0.06) {
            mob.setPos(tx, y, tz);
        }
    }


    private PlanExecutor() {}
}
