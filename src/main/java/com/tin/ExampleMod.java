package com.tin;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tin.mapf.commands.MapfCommand;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "mapf_mod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
        MapfCommand.register();
		LOGGER.info("Hello Fabric world!");
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            // Use Overworld time as “now”. This runs once per server tick.
            long now = server.overworld().getGameTime();
            com.tin.mapf.plan.CoopPlanner.tick(now);
        });
	}
}