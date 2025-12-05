package com.tin.mixin;

import com.mojang.logging.LogUtils;
import com.tin.mapf.goals.CooperativeNavigateGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.npc.Villager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class VillagerGoalMixin {
    private static final Logger LOG = LogUtils.getLogger();

    @Shadow(remap = false) protected GoalSelector goalSelector;

    // IMPORTANT: remap = false so the processor uses Mojang names in dev
    @Inject(method = "registerGoals", at = @At("TAIL"), remap = false)
    private void mapf$installCooperativeGoal(CallbackInfo ci) {
        if (!(((Object) this) instanceof Villager)) return;
        Mob self = (Mob) (Object) this;
        goalSelector.addGoal(0, new CooperativeNavigateGoal(self));
        LOG.info("[MAPF] Registered CooperativeNavigateGoal on villager {}", self.getUUID());
    }
}
