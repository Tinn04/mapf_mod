package com.tin.mapf.goals;

import com.tin.mapf.commands.MapfCommand;
import com.tin.mapf.exec.PlanExecutor;
import com.tin.mapf.plan.CoopPlanner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.EnumSet;

public class CooperativeNavigateGoal extends Goal {
    private final Mob mob;

    public CooperativeNavigateGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public void start() {
        mob.getNavigation().stop(); // drop any vanilla path
        System.out.println("[MAPF] Goal start for " + mob.getUUID());
    }

    @Override
    public boolean canUse() {
        // Marked as an agent? then occupy the goal slot so the villager stands still
        return com.tin.mapf.commands.MapfCommand.AGENTS.contains(mob.getUUID());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (!com.tin.mapf.plan.CoopPlanner.isRunning()) {
            // kill any new wander intents
            Brain<?> brain = mob.getBrain();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);

            mob.getNavigation().stop();
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            return;
        }
        long now = ((net.minecraft.server.level.ServerLevel) mob.level()).getGameTime();
        com.tin.mapf.plan.CoopPlanner.tick(now);
        com.tin.mapf.exec.PlanExecutor.tickFollow(mob, (int) now);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        com.tin.mapf.plan.CoopPlanner.remove(mob.getUUID());
    }

}
