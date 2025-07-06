package net.countered.settlementroads;

import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.events.ModEventHandler;
import net.countered.settlementroads.features.RoadFeature;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SettlementRoads implements ModInitializer {

	public static final String MOD_ID = "settlement-roads";

	public static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

	// TODO
	// IMPORTANT

	// OPTIONAL
	// Possibly broken roads when recaching on world reload?
	// Biome specific road changes
	// Location lag reducing (async locator?)/ structure essentials / place instant roads?
	// Bridges
	// Tunnels
	// fix roads on snow icebergs
	// Remove placed blocks from caches
	// place slabs on artificial roads

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Settlement Roads...");
		ModConfig.init(MOD_ID, ModConfig.class);
		registerFeatures();
		ModEventHandler.register();
	}

	private void registerFeatures() {
		LOGGER.info("Registering Features...");
		Registry.register(Registries.FEATURE, Identifier.of(MOD_ID, "road_feature"), RoadFeature.ROAD_FEATURE);
		BiomeModifications.addFeature(
				BiomeSelectors.all(),
				GenerationStep.Feature.UNDERGROUND_STRUCTURES,
				RoadFeature.ROAD_FEATURE_PLACED_KEY
		);
	}
}