package com.tin.mapf.plan;

public enum Algo {
    WHCA, CBS, SINGLE;

    public static Algo from(String s) {
        return switch (s.toLowerCase()) {
            case "whca", "whca*", "windowed" -> WHCA;
            case "cbs" -> CBS;
            case "single", "a*", "astar" -> SINGLE;
            default -> WHCA;
        };
    }
}
