package com.tin.mapf.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tin.mapf.grid.CellKey;
import com.tin.mapf.plan.CoopPlanner;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * /mapf commands with named agents (Agent1, Agent2, ...),
 * colored glow & nametags, start/stop/reset, arena builder, and QoL helpers.
 */
public final class MapfCommand {
    // Core state
    public static final Set<UUID> AGENTS = new HashSet<>();
    public static final Map<UUID, CellKey> GOALS = new HashMap<>();

    // ---- Named agents ----
    private static final Map<String, UUID> AGENT_BY_NAME = new HashMap<>();
    private static final Map<UUID, String> NAME_BY_AGENT = new HashMap<>();
    private static final TreeSet<Integer> FREE_NUMS = new TreeSet<>();
    private static int NEXT_NUM = 1;

    // ---- Team / glow (color source for outline & chat) ----
    private static final Map<UUID, String> AGENT_TEAMS = new HashMap<>();
    private static final ChatFormatting[] TEAM_COLORS = {
            ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.GOLD,
            ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.DARK_GRAY
    };
    private static int colorIdx = 0;

    // ---- Freeze via attribute modifier (1.21.x) ----
    private static final ResourceLocation FREEZE_MOD_ID =
            ResourceLocation.fromNamespaceAndPath("mapf_mod", "freeze_speed");

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("mapf")

                    // /mapf arena <feetY> <size> [keep]
                    .then(Commands.literal("arena")
                            .then(Commands.argument("feetY", IntegerArgumentType.integer(0, 319))
                                    .then(Commands.argument("size", IntegerArgumentType.integer(6, 256))
                                            // Normal: build floor + clear air + border
                                            .executes(ctx -> {
                                                CommandSourceStack src = ctx.getSource();
                                                ServerPlayer sp = src.getPlayer();
                                                if (sp == null) return 0;

                                                final int feetY = IntegerArgumentType.getInteger(ctx, "feetY");
                                                final int size  = IntegerArgumentType.getInteger(ctx, "size");

                                                final int cx = sp.blockPosition().getX();
                                                final int cz = sp.blockPosition().getZ();
                                                final int half = size / 2;

                                                final int minX = cx - half;
                                                final int maxX = minX + size - 1;
                                                final int minZ = cz - half;
                                                final int maxZ = minZ + size - 1;

                                                ServerLevel level = src.getLevel();

                                                buildArenaArea(level, feetY, minX, maxX, minZ, maxZ, /*keepInterior=*/false);
                                                CoopPlanner.configure(level, feetY, minX, maxX, minZ, maxZ);

                                                src.sendSuccess(() -> text(
                                                        "Arena set: y=" + feetY + " size=" + size +
                                                                " bounds=[" + minX + "," + minZ + "]..[" + maxX + "," + maxZ + "]"
                                                ), true);
                                                return 1;
                                            })
                                            // Optional: "keep" => draw ONLY the border; do NOT touch interior
                                            .then(Commands.literal("keep")
                                                    .executes(ctx -> {
                                                        CommandSourceStack src = ctx.getSource();
                                                        ServerPlayer sp = src.getPlayer();
                                                        if (sp == null) return 0;

                                                        final int feetY = IntegerArgumentType.getInteger(ctx, "feetY");
                                                        final int size  = IntegerArgumentType.getInteger(ctx, "size");

                                                        final int cx = sp.blockPosition().getX();
                                                        final int cz = sp.blockPosition().getZ();
                                                        final int half = size / 2;

                                                        final int minX = cx - half;
                                                        final int maxX = minX + size - 1;
                                                        final int minZ = cz - half;
                                                        final int maxZ = minZ + size - 1;

                                                        ServerLevel level = src.getLevel();

                                                        buildArenaArea(level, feetY, minX, maxX, minZ, maxZ, /*keepInterior=*/true);
                                                        CoopPlanner.configure(level, feetY, minX, maxX, minZ, maxZ);

                                                        src.sendSuccess(() -> text(
                                                                "Arena set (keep interior): y=" + feetY + " size=" + size +
                                                                        " bounds=[" + minX + "," + minZ + "]..[" + maxX + "," + maxZ + "]"
                                                        ), true);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                    )


                    // /mapf algo <whca|cbs|single>
                    .then(Commands.literal("algo")
                            .then(Commands.argument("name", StringArgumentType.string())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        var a = com.tin.mapf.plan.Algo.from(name);
                                        com.tin.mapf.plan.CoopPlanner.CURRENT = a;
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("Planner set to " + a), true);
                                        return 1;
                                    })
                            )
                    )

                    // /mapf window <W>  (WHCA* lookahead)
                    .then(Commands.literal("window")
                            .then(Commands.argument("W", IntegerArgumentType.integer(10, 1024))
                                    .executes(ctx -> {
                                        int W = IntegerArgumentType.getInteger(ctx, "W");
                                        com.tin.mapf.plan.WhcaPlanner.WINDOW = W;
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("WHCA* window = " + W), true);
                                        return 1;
                                    })
                            )
                    )

                    // /mapf replan <R>  (replan cadence)
                    .then(Commands.literal("replan")
                            .then(Commands.argument("R", IntegerArgumentType.integer(1, 60))
                                    .executes(ctx -> {
                                        int R = IntegerArgumentType.getInteger(ctx, "R");
                                        com.tin.mapf.plan.CoopPlanner.REPLAN_PERIOD = R;
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("Replan period = " + R + " ticks"), true);
                                        return 1;
                                    })
                            )
                    )

                    // /mapf agent <target>  (toggle villager as agent)
                    .then(Commands.literal("agent")
                            .then(Commands.argument("target", EntityArgument.entity())
                                    .executes(ctx -> {
                                        LivingEntity e = (LivingEntity) EntityArgument.getEntity(ctx, "target");
                                        if (!(e instanceof Villager)) {
                                            ctx.getSource().sendFailure(text("Target must be a Villager."));
                                            return 0;
                                        }
                                        UUID id = e.getUUID();
                                        if (AGENTS.remove(id)) {
                                            // OFF
                                            ChatFormatting col = agentColor(id, ctx.getSource());
                                            String name = NAME_BY_AGENT.getOrDefault(id, id.toString());
                                            clearGlowAndTeam(ctx.getSource(), e);
                                            removeFreeze(e);
                                            releaseName(id);
                                            GOALS.remove(id);
                                            ctx.getSource().sendSuccess(() -> colored("Removed " + name + " (" + id + ")", col), true);
                                        } else {
                                            // ON
                                            AGENTS.add(id);
                                            String name = assignName(id);
                                            applyGlowAndTeam(ctx.getSource(), e);
                                            if (e instanceof Mob m) m.getNavigation().stop();
                                            addFreeze(e); // stand still until /mapf start
                                            ChatFormatting col = agentColor(id, ctx.getSource());
                                            ctx.getSource().sendSuccess(() -> colored("Added " + name + ": " + id, col), true);
                                        }
                                        return 1;
                                    })
                            )
                    )

                    // /mapf list
                    .then(Commands.literal("list")
                            .executes(ctx -> {
                                if (AGENTS.isEmpty()) {
                                    ctx.getSource().sendSuccess(() -> text("No agents."), false);
                                    return 1;
                                }
                                for (UUID id : AGENTS) {
                                    String name = NAME_BY_AGENT.getOrDefault(id, id.toString());
                                    CellKey g = GOALS.get(id);
                                    String base = name + " -> " + id + (g == null ? "" : (" goal=(" + g.x + "," + g.z + ")"));
                                    ctx.getSource().sendSuccess(() -> colored(base, agentColor(id, ctx.getSource())), false);
                                }
                                return 1;
                            })
                    )

                    // /mapf goal <AgentName> <gx> <gz>
                    .then(Commands.literal("goal")
                            .then(Commands.argument("who", StringArgumentType.word())
                                    .then(Commands.argument("gx", IntegerArgumentType.integer())
                                            .then(Commands.argument("gz", IntegerArgumentType.integer())
                                                    .executes(ctx -> {
                                                        String who = StringArgumentType.getString(ctx, "who");
                                                        UUID id = resolveAgentByNameOrUuid(who);
                                                        if (id == null) {
                                                            ctx.getSource().sendFailure(text("Unknown agent: " + who));
                                                            return 0;
                                                        }
                                                        if (!CoopPlanner.hasArena()) {
                                                            ctx.getSource().sendFailure(text("Set arena first: /mapf arena <feetY>"));
                                                            return 0;
                                                        }
                                                        int gx = IntegerArgumentType.getInteger(ctx, "gx");
                                                        int gz = IntegerArgumentType.getInteger(ctx, "gz");
                                                        int clampedX = Math.max(CoopPlanner.minX(), Math.min(CoopPlanner.maxX(), gx));
                                                        int clampedZ = Math.max(CoopPlanner.minZ(), Math.min(CoopPlanner.maxZ(), gz));
                                                        if (clampedX != gx || clampedZ != gz) {
                                                            ctx.getSource().sendSuccess(() ->
                                                                    text("Goal clamped to arena: (" + clampedX + "," + clampedZ + ")"), false);
                                                        }
                                                        GOALS.put(id, new CellKey(clampedX, clampedZ));
                                                        String name = NAME_BY_AGENT.getOrDefault(id, id.toString());
                                                        String base = "Set goal for " + name + " -> (" + clampedX + "," + clampedZ + ")";
                                                        ctx.getSource().sendSuccess(() ->
                                                                colored(base, agentColor(id, ctx.getSource())), true);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            // Old form: /mapf goal <target> <gx> <gz>
                            .then(Commands.argument("target", EntityArgument.entity())
                                    .then(Commands.argument("gx", IntegerArgumentType.integer())
                                            .then(Commands.argument("gz", IntegerArgumentType.integer())
                                                    .executes(ctx -> {
                                                        LivingEntity e = (LivingEntity) EntityArgument.getEntity(ctx, "target");
                                                        if (e == null) return 0;
                                                        if (!CoopPlanner.hasArena()) {
                                                            ctx.getSource().sendFailure(text("Set arena first: /mapf arena <feetY>"));
                                                            return 0;
                                                        }
                                                        int gx = IntegerArgumentType.getInteger(ctx, "gx");
                                                        int gz = IntegerArgumentType.getInteger(ctx, "gz");
                                                        int clampedX = Math.max(CoopPlanner.minX(), Math.min(CoopPlanner.maxX(), gx));
                                                        int clampedZ = Math.max(CoopPlanner.minZ(), Math.min(CoopPlanner.maxZ(), gz));
                                                        if (clampedX != gx || clampedZ != gz) {
                                                            ctx.getSource().sendSuccess(() ->
                                                                    text("Goal clamped to arena: (" + clampedX + "," + clampedZ + ")"), false);
                                                        }
                                                        GOALS.put(e.getUUID(), new CellKey(clampedX, clampedZ));
                                                        String name = NAME_BY_AGENT.getOrDefault(e.getUUID(), e.getUUID().toString());
                                                        String base = "Set goal for " + name + " -> (" + clampedX + "," + clampedZ + ")";
                                                        ctx.getSource().sendSuccess(() ->
                                                                colored(base, agentColor(e.getUUID(), ctx.getSource())), true);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                    )

                    // /mapf start
                    .then(Commands.literal("start").executes(ctx -> {
                        if (!CoopPlanner.hasArena()) {
                            ctx.getSource().sendFailure(text("Set arena first: /mapf arena <feetY>"));
                            return 0;
                        }
                        for (UUID id : AGENTS) {
                            LivingEntity e = (LivingEntity) ctx.getSource().getLevel().getEntity(id);
                            if (e != null) removeFreeze(e);
                        }
                        CoopPlanner.setRunning(true);
                        ctx.getSource().sendSuccess(() -> text("MAPF started."), true);
                        return 1;
                    }))

                    // /mapf stop
                    .then(Commands.literal("stop").executes(ctx -> {
                        CoopPlanner.setRunning(false);
                        for (UUID id : AGENTS) {
                            LivingEntity e = (LivingEntity) ctx.getSource().getLevel().getEntity(id);
                            if (e != null) {
                                if (e instanceof Mob m) m.getNavigation().stop();
                                addFreeze(e);
                            }
                        }
                        ctx.getSource().sendSuccess(() -> text("MAPF paused."), true);
                        return 1;
                    }))

                    // /mapf reset
                    .then(Commands.literal("reset").executes(ctx -> {
                        for (UUID id : new ArrayList<>(AGENTS)) {
                            LivingEntity e = (LivingEntity) ctx.getSource().getLevel().getEntity(id);
                            if (e != null) {
                                clearGlowAndTeam(ctx.getSource(), e);
                                removeFreeze(e);
                            }
                            releaseName(id);
                        }
                        AGENTS.clear();
                        GOALS.clear();
                        CoopPlanner.resetAll();
                        ctx.getSource().sendSuccess(() -> text("MAPF state reset."), true);
                        return 1;
                    }))
                    // /mapf label <free text>
                    .then(Commands.literal("label")
                            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                    .<net.minecraft.commands.CommandSourceStack, String>argument("name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        com.tin.mapf.log.ExpLog.LABEL = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                        ctx.getSource().sendSuccess(() ->
                                                net.minecraft.network.chat.Component.literal("Label set: " + com.tin.mapf.log.ExpLog.LABEL), true);
                                        return 1;
                                    })
                            )
                    )

                    // /mapf logrun  (manual run snapshot)
                    .then(Commands.literal("logrun")
                            .executes(ctx -> {
                                int gridW = com.tin.mapf.plan.CoopPlanner.maxX() - com.tin.mapf.plan.CoopPlanner.minX() + 1;
                                int gridH = com.tin.mapf.plan.CoopPlanner.maxZ() - com.tin.mapf.plan.CoopPlanner.minZ() + 1;
                                com.tin.mapf.log.ExpLog.logRun(com.tin.mapf.log.ExpLog.map(
                                        "ts_real_ms", System.currentTimeMillis(),
                                        "session",    com.tin.mapf.log.ExpLog.SESSION.get(),
                                        "label",      com.tin.mapf.log.ExpLog.LABEL,
                                        "algo",       com.tin.mapf.plan.CoopPlanner.CURRENT,
                                        "R",          com.tin.mapf.plan.CoopPlanner.REPLAN_PERIOD,
                                        "W",          com.tin.mapf.plan.WhcaPlanner.WINDOW,
                                        "feetY",      com.tin.mapf.plan.CoopPlanner.arenaFeetY(),
                                        "minX",       com.tin.mapf.plan.CoopPlanner.minX(),
                                        "maxX",       com.tin.mapf.plan.CoopPlanner.maxX(),
                                        "minZ",       com.tin.mapf.plan.CoopPlanner.minZ(),
                                        "maxZ",       com.tin.mapf.plan.CoopPlanner.maxZ(),
                                        "grid_w",     gridW, "grid_h", gridH,
                                        "agents_n",   AGENTS.size(),
                                        "goals_n",    GOALS.size(),
                                        "makespan_ticks", 0,
                                        "soc_steps",      0
                                ));
                                ctx.getSource().sendSuccess(() ->
                                        net.minecraft.network.chat.Component.literal("Run snapshot logged."), true);
                                return 1;
                            })
                    )
                    // alias
                    .then(Commands.literal("clear").executes(ctx -> dispatcher.execute("mapf reset", ctx.getSource())))
            );
        });
    }

    // ---------- naming helpers ----------
    private static String assignName(UUID id) {
        if (NAME_BY_AGENT.containsKey(id)) return NAME_BY_AGENT.get(id);
        int num = FREE_NUMS.isEmpty() ? NEXT_NUM++ : FREE_NUMS.pollFirst();
        String name = "Agent" + num;
        AGENT_BY_NAME.put(name, id);
        NAME_BY_AGENT.put(id, name);
        return name;
    }

    private static void releaseName(UUID id) {
        String name = NAME_BY_AGENT.remove(id);
        if (name != null) {
            AGENT_BY_NAME.remove(name);
            if (name.startsWith("Agent")) {
                try {
                    int n = Integer.parseInt(name.substring("Agent".length()));
                    FREE_NUMS.add(n);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private static UUID resolveAgentByNameOrUuid(String who) {
        UUID id = AGENT_BY_NAME.get(who);
        if (id != null) return id;
        try { return UUID.fromString(who); } catch (IllegalArgumentException ignored) {}
        return null;
    }

    // ---------- color & chat helpers ----------
    private static ChatFormatting agentColor(UUID id, CommandSourceStack src) {
        String teamName = AGENT_TEAMS.get(id);
        if (teamName == null) return ChatFormatting.WHITE;
        PlayerTeam team = src.getServer().getScoreboard().getPlayerTeam(teamName);
        return team != null ? team.getColor() : ChatFormatting.WHITE;
    }

    private static Component colored(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(color);
    }

    private static Component text(String s) {
        return Component.literal(s);
    }

    // ---------- visuals (glow, team, colored nametag) ----------
    private static void applyGlowAndTeam(CommandSourceStack src, LivingEntity e) {
        Scoreboard sb = src.getServer().getScoreboard();
        String entry = e.getStringUUID();
        String teamName = "mapf_" + entry.substring(0, 8);

        PlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) {
            team = sb.addPlayerTeam(teamName);
            team.setColor(TEAM_COLORS[colorIdx % TEAM_COLORS.length]);
            colorIdx++;
        }
        sb.addPlayerToTeam(entry, team);
        AGENT_TEAMS.put(e.getUUID(), teamName);

        // Colored name tag matching glow color
        String name = NAME_BY_AGENT.getOrDefault(e.getUUID(), e.getUUID().toString());
        ChatFormatting col = team.getColor();
        e.setCustomName(Component.literal(name).withStyle(col));
        e.setCustomNameVisible(true);

        // Enable glowing (outline colored by team)
        e.setGlowingTag(true);
    }

    private static void clearGlowAndTeam(CommandSourceStack src, LivingEntity e) {
        e.setGlowingTag(false);
        e.setCustomName(null);
        e.setCustomNameVisible(false);

        Scoreboard sb = src.getServer().getScoreboard();
        String entry = e.getStringUUID();
        String teamName = AGENT_TEAMS.remove(e.getUUID());
        if (teamName != null) {
            PlayerTeam team = sb.getPlayerTeam(teamName);
            if (team != null) {
                sb.removePlayerFromTeam(entry, team);
                if (team.getPlayers().isEmpty()) sb.removePlayerTeam(team);
            }
        }
    }

    // ---------- freeze helpers (attribute-based, 1.21.x) ----------
    public static void addFreeze(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null && inst.getModifier(FREEZE_MOD_ID) == null) {
            // finalSpeed = baseSpeed * (1 + amount); amount = -1.0 -> 0% speed
            inst.addPermanentModifier(new AttributeModifier(
                    FREEZE_MOD_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (e instanceof Mob m) m.getNavigation().stop();
    }

    public static void removeFreeze(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) inst.removeModifier(FREEZE_MOD_ID);
    }
    // Convenience: freeze/unfreeze by UUID from server level
    public static void freezeById(ServerLevel level, java.util.UUID id) {
        var ent = level.getEntity(id);
        if (ent instanceof net.minecraft.world.entity.LivingEntity le) {
            addFreeze(le);
            if (le instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
        }
    }
    public static void unfreezeById(ServerLevel level, java.util.UUID id) {
        var ent = level.getEntity(id);
        if (ent instanceof net.minecraft.world.entity.LivingEntity le) removeFreeze(le);
    }

    // ---------- arena builder ----------
    private static void buildArenaArea(ServerLevel level, int feetY,
                                       int minX, int maxX, int minZ, int maxZ,
                                       boolean keepInterior) {
        // Grid interior is [minX..maxX] × [minZ..maxZ]
        // Border goes one block OUTSIDE that rectangle:
        final int bMinX = minX - 1, bMaxX = maxX + 1;
        final int bMinZ = minZ - 1, bMaxZ = maxZ + 1;

        final int floorY = feetY - 1; // walking floor
        final int airY1  = feetY;     // feet
        final int airY2  = feetY + 1; // head

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 1) Interior (optional)
        if (!keepInterior) {
            // Floor: WHITE_CONCRETE
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, floorY, z);
                    level.setBlock(pos, Blocks.WHITE_CONCRETE.defaultBlockState(), 18);
                }
            }
            // Clear walking/head space (AIR)
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, airY1, z);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                    pos.set(x, airY2, z);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                }
            }
        }

        // 2) Border ring (2 blocks tall) using STONE_BRICKS
        for (int x = bMinX; x <= bMaxX; x++) {
            // south edge at bMinZ
            pos.set(x, airY1, bMinZ); level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 18);
            pos.set(x, airY2, bMinZ); level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 18);
            // north edge at bMaxZ
            pos.set(x, airY1, bMaxZ); level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 18);
            pos.set(x, airY2, bMaxZ); level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 18);
        }
        for (int z = bMinZ + 1; z <= bMaxZ - 1; z++) {
            // west edge at bMinX
            pos.set(bMinX, airY1, z); level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 18);
            pos.set(bMinX, airY2, z); level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 18);
            // east edge at bMaxX
            pos.set(bMaxX, airY1, z); level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 18);
            pos.set(bMaxX, airY2, z); level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 18);
        }
    }
    public static String nameOf(UUID id) {
        return NAME_BY_AGENT != null
                ? NAME_BY_AGENT.getOrDefault(id, id.toString())
                : id.toString();
    }

}
