package com.tin.mapf.plan;

public final class Heuristics {
    public static int manhattan(int x, int z, int gx, int gz) {
        return Math.abs(x - gx) + Math.abs(z - gz);
    }
    private Heuristics(){}
}
