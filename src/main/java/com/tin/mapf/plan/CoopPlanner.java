package com.tin.mapf.plan;

import com.tin.mapf.commands.MapfCommand;
import com.tin.mapf.grid.CellKey;
import com.tin.mapf.grid.GridBuilder;
import com.tin.mapf.time.ReservationKey;
import com.tin.mapf.time.ReservationTable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import com.tin.mapf.log.ExpLog;


import java.util.*;

/**
 * Windowed cooperative planner (replans every R ticks for window W).
 * Arena Y is treated as the **feet (air) layer**. Floor is (feetY - 1).
 */
public final class CoopPlanner {
    // ---- Runtime state (unset until /mapf arena) ----
    private static ServerLevel LEVEL = null;
    private static GridBuilder GRID = null;        // expects feetY (air) in ctor
    private static int ARENA_FEET_Y = -1;          // -1 = not configured yet
    private static int MIN_X = 0, MAX_X = -1, MIN_Z = 0, MAX_Z = -1;
    public static final Map<UUID, Integer> STEPS = new HashMap<>();
    public static final Map<UUID, String> LAST_CELL = new HashMap<>();

    // Start and Stop
    private static boolean RUNNING = false;
    public static boolean isRunning() { return RUNNING; }
    public static void setRunning(boolean running) { RUNNING = running; }

    // ---- Reservations & active plans ----
    private static final ReservationTable RES = new ReservationTable();
    private static final Map<UUID, List<ReservationKey>> ACTIVE = new HashMap<>();

    // ---- Planner selection & cadence ----
    public static com.tin.mapf.plan.Algo CURRENT = com.tin.mapf.plan.Algo.WHCA;
    public static int REPLAN_PERIOD = 10; // you already use 10
    private static final Planner WHCA = new WhcaPlanner();
    private static final Planner CBS  = new CbsPlanner();
    private static final Planner SINGLE = new SinglePlanner();

    // ---- Public configuration API ----
    /** Configure planner with arena (feetY = air layer) and bounds (inclusive). */
    public static void configure(ServerLevel level, int feetY, int minX, int maxX, int minZ, int maxZ) {
        LEVEL = level;
        ARENA_FEET_Y = feetY;
        MIN_X = Math.min(minX, maxX);
        MAX_X = Math.max(minX, maxX);
        MIN_Z = Math.min(minZ, maxZ);
        MAX_Z = Math.max(minZ, maxZ);
        GRID = new GridBuilder(level, feetY);

        ExpLog.initFor(level);
        // fresh reservations whenever arena changes
        RES.clearAll();
        ACTIVE.clear();
        System.out.println("[MAPF] Arena set: y=" + feetY + " bounds=[" + MIN_X + "," + MIN_Z + "]..[" + MAX_X + "," + MAX_Z + "]");
    }

    /** Hard reset of all runtime state (use before/after tests). */
    public static void resetAll() {
        ExpLog.SESSION.incrementAndGet();
        RUNNING = false;
        ACTIVE.clear();
        RES.clearAll();
        LEVEL = null;
        GRID  = null;
        ARENA_FEET_Y = -1;
        MIN_X = 0; MAX_X = -1; MIN_Z = 0; MAX_Z = -1;
        System.out.println("[MAPF] State reset.");
    }

    /** Drop all reservations/plan for a specific agent (call on stop/despawn). */
    public static void remove(UUID id) {
        ACTIVE.remove(id);
        RES.clearFor(id);
    }

    // ---- Accessors used by executor/commands ----
    public static boolean hasArena() { return GRID != null && ARENA_FEET_Y >= -64 && MIN_X <= MAX_X && MIN_Z <= MAX_Z; }
    public static int arenaFeetY() { return ARENA_FEET_Y; }
    public static int minX() { return MIN_X; }
    public static int maxX() { return MAX_X; }
    public static int minZ() { return MIN_Z; }
    public static int maxZ() { return MAX_Z; }

    private static boolean inBounds(int x, int z) {
        return x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z;
    }

    /** Look up the reserved cell for an agent at a specific tick. */
    public static Optional<CellKey> cellAt(UUID id, int tick) {
        List<ReservationKey> p = ACTIVE.get(id);
        if (p == null) return Optional.empty();
        for (ReservationKey rk : p) if (rk.tick == tick) return Optional.of(rk.cell);
        return Optional.empty();
    }
    private static long LAST_TICK = Long.MIN_VALUE;
    // ---- Main planning loop (call every server tick) ----
    public static void tick(long now) {
        if (now == LAST_TICK) return;   // prevent double execution in the same server tick
        LAST_TICK = now;
        if (!hasArena() || LEVEL == null || !RUNNING) return;
        if ((now % REPLAN_PERIOD) != 0) return;

        RES.pruneBefore((int) now - 1);

        for (UUID id : MapfCommand.AGENTS) {
            CellKey goal = MapfCommand.GOALS.get(id);
            if (goal == null || MapfCommand.ARRIVED.getOrDefault(id, false)) continue;
            if (!inBounds(goal.x, goal.z)) continue;  // ignore out-of-bounds goals

            Entity e = LEVEL.getEntity(id);
            if (e == null) { remove(id); continue; }

            int sx = e.blockPosition().getX();
            int sz = e.blockPosition().getZ();
            if (!inBounds(sx, sz)) {
                // clamp starts that drifted out of bounds
                sx = Math.max(MIN_X, Math.min(MAX_X, sx));
                sz = Math.max(MIN_Z, Math.min(MAX_Z, sz));
            }

            if (sx == goal.x && sz == goal.z && !MapfCommand.ARRIVED.getOrDefault(id, false)) {
                MapfCommand.ARRIVED.put(id, true);
                int nowInt = (int) now;

                var hold = java.util.List.of(new ReservationKey(new CellKey(sx, sz), nowInt + 1));
                RES.reserve(id, hold);
                ACTIVE.put(id, hold);

                com.tin.mapf.commands.MapfCommand.freezeById(LEVEL, id);
                //com.tin.mapf.commands.MapfCommand.GOALS.remove(id);
                int gridW = (maxX() - minX() + 1), gridH = (maxZ() - minZ() + 1);

                ExpLog.logPlan(ExpLog.map(
                        "ts_real_ms", System.currentTimeMillis(),
                        "tick_now",   now,
                        "session",    ExpLog.SESSION.get(),
                        "label",      ExpLog.LABEL,
                        "algo",       CURRENT,
                        "R",          REPLAN_PERIOD,
                        "W",          com.tin.mapf.plan.WhcaPlanner.WINDOW,
                        "feetY",      arenaFeetY(),
                        "minX",       minX(), "maxX", maxX(), "minZ", minZ(), "maxZ", maxZ(),
                        "grid_w",     gridW,  "grid_h", gridH,
                        "agent_name", com.tin.mapf.commands.MapfCommand.nameOf(id),
                        "agent_uuid", id,
                        "sx", sx, "sz", sz, "gx", goal.x, "gz", goal.z,
                        "manhattan",  Math.abs(sx - goal.x) + Math.abs(sz - goal.z),
                        "time_ms",    0.0,
                        "nodes_expanded", 0,
                        "planned_len", 0,
                        "reserved_prefix_len", 1,
                        "success", 1,
                        "at_goal", 1,
                        "goal_cleared", 1,
                        "cbs_splits", 0,
                        "steps", STEPS.getOrDefault(id, 0)
                ));
                continue;
            }

            // pick planner
            Planner planner = switch (CURRENT) {
                case WHCA   -> WHCA;
                case CBS    -> CBS;
                case SINGLE -> SINGLE;
            };

            // --- measure planning time and expansions ---
            // --- measure planning time and expansions ---
            long t0 = System.nanoTime();
// reset per-plan expansion counter (see TimeAStar patch)
            com.tin.mapf.plan.TimeAStar.Counters.takeAndReset();

            List<ReservationKey> plan =
                    planner.plan(LEVEL, GRID, RES, id, sx, sz, goal.x, goal.z, (int) now);

            long t1 = System.nanoTime();
            int nodes = com.tin.mapf.plan.TimeAStar.Counters.takeAndReset(); // expanded during this plan

            int gridW = (maxX() - minX() + 1), gridH = (maxZ() - minZ() + 1);
            boolean atGoal = (sx == goal.x && sz == goal.z);
            boolean success = !plan.isEmpty();
            int splits = (CURRENT == com.tin.mapf.plan.Algo.CBS) ? com.tin.mapf.plan.CbsPlanner.cbsSplitsLast : 0;


// reserve only a prefix when windowed / periodic replanning
            int prefixLen = success
                    ? ((CURRENT == com.tin.mapf.plan.Algo.CBS) ? plan.size() : Math.min(REPLAN_PERIOD, plan.size()))
                    : 0;

// --- write one CSV row per planning attempt ---
            ExpLog.logPlan(ExpLog.map(
                    "ts_real_ms", System.currentTimeMillis(),
                    "tick_now",   now,
                    "session",    ExpLog.SESSION.get(),
                    "label",      ExpLog.LABEL,
                    "algo",       CURRENT,
                    "R",          REPLAN_PERIOD,
                    "W",          com.tin.mapf.plan.WhcaPlanner.WINDOW,
                    "feetY",      arenaFeetY(),
                    "minX",       minX(), "maxX", maxX(), "minZ", minZ(), "maxZ", maxZ(),
                    "grid_w",     gridW,  "grid_h", gridH,
                    "agent_name", com.tin.mapf.commands.MapfCommand.nameOf(id),
                    "agent_uuid", id,
                    "sx", sx, "sz", sz, "gx", goal.x, "gz", goal.z,
                    "manhattan",  Math.abs(sx - goal.x) + Math.abs(sz - goal.z),
                    "time_ms",    (t1 - t0) / 1_000_000.0,
                    "nodes_expanded", nodes,
                    "planned_len", plan.size(),
                    "reserved_prefix_len", prefixLen,
                    "success", success ? 1 : 0,
                    "at_goal", atGoal ? 1 : 0,
                    "goal_cleared", 0,
                    "cbs_splits", splits,
                    "steps", STEPS.getOrDefault(id, 0)
            ));

            if (success) {
                List<ReservationKey> prefix = plan.subList(0, prefixLen);
                RES.reserve(id, prefix);
                ACTIVE.put(id, plan);
                var first = prefix.get(0).cell;
                System.out.println("[MAPF][" + CURRENT + "] planned " + plan.size() + " steps for " + id +
                        " first=(" + first.x + "," + first.z + ") now=" + now);
            } else {
                // --- NO PLAN debug dump (unchanged) ---
                int gx = goal.x, gz = goal.z;
                int y  = ARENA_FEET_Y;
                boolean startWalk = GRID.isWalkable(sx, sz);
                boolean goalWalk  = GRID.isWalkable(gx, gz);
                BlockState floorS = LEVEL.getBlockState(new BlockPos(sx, y - 1, sz));
                BlockState feetS  = LEVEL.getBlockState(new BlockPos(sx, y,     sz));
                BlockState headS  = LEVEL.getBlockState(new BlockPos(sx, y + 1, sz));
                BlockState floorG = LEVEL.getBlockState(new BlockPos(gx, y - 1, gz));
                BlockState feetG  = LEVEL.getBlockState(new BlockPos(gx, y,     gz));
                BlockState headG  = LEVEL.getBlockState(new BlockPos(gx, y + 1, gz));
                System.out.println("[MAPF][" + CURRENT + "] NO PLAN for " + id + " at now=" + now +
                        " start=(" + sx + "," + sz + ") walkable=" + startWalk +
                        " floorS=" + floorS.getBlock() + " feetS=" + feetS.getBlock() + " headS=" + headS.getBlock() +
                        " | goal=(" + gx + "," + gz + ") walkable=" + goalWalk +
                        " floorG=" + floorG.getBlock() + " feetG=" + feetG.getBlock() + " headG=" + headG.getBlock());
            }


        }
        boolean allDone = true;
        for (UUID id2 : MapfCommand.AGENTS) {
            if (MapfCommand.GOALS.get(id2) != null && !MapfCommand.ARRIVED.getOrDefault(id2, false)) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            setRunning(false);
            System.out.println("[MAPF] All agents arrived. Stopping at tick=" + now);
        }
    }
    public static Optional<CellKey> goalOf(UUID id) {
        return Optional.ofNullable(com.tin.mapf.commands.MapfCommand.GOALS.get(id));
    }

    private CoopPlanner() {}
}
