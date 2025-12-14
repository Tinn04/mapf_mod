package com.tin.mapf.log;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExpLog {
    private static CsvLog PLANS, RUNS;
    public static volatile String LABEL = "";
    public static final AtomicInteger SESSION = new AtomicInteger(0);

    public static void initFor(ServerLevel level) {
        // write logs under the world folder: <world>/mapf_logs/
        Path worldDir = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dir = worldDir.resolve("mapf_logs");
        PLANS = new CsvLog(dir, "plans.csv");
        RUNS  = new CsvLog(dir, "runs.csv");

        PLANS.writeHeader(String.join(",",
                "ts_real_ms","tick_now","session","label","algo","R","W",
                "feetY","minX","maxX","minZ","maxZ","grid_w","grid_h",
                "agent_name","agent_uuid","sx","sz","gx","gz","manhattan",
                "time_ms","nodes_expanded","planned_len","reserved_prefix_len",
                "success","at_goal","goal_cleared","cbs_splits", "steps"));

        RUNS.writeHeader(String.join(",",
                "ts_real_ms","session","label","algo","R","W",
                "feetY","minX","maxX","minZ","maxZ","grid_w","grid_h",
                "agents_n","goals_n","makespan_ticks","soc_steps"));
    }

    public static void logPlan(Map<String,Object> row) {
        if (PLANS != null) PLANS.writeRow(CsvLog.csv(row));
    }
    public static void logRun(Map<String,Object> row) {
        if (RUNS != null) RUNS.writeRow(CsvLog.csv(row));
    }

    public static Map<String,Object> map(Object... kv) {
        Map<String,Object> m = new LinkedHashMap<>();
        for (int i=0;i<kv.length;i+=2) m.put(kv[i].toString(), kv[i+1]);
        return m;
    }

    private ExpLog(){}
}
