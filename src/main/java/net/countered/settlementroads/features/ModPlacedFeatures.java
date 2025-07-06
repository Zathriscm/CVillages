package net.countered.settlementroads.features;

import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.features.RoadFeature;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.placementmodifier.BiomePlacementModifier;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightmapPlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ModPlacedFeatures {

    public static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static void bootstrap(Registerable<PlacedFeature> context){
        LOGGER.info("Bootstrap PlacedFeature");
        var configuredFeatureRegistryEntryLookup = context.getRegistryLookup(RegistryKeys.CONFIGURED_FEATURE);

        context.register(RoadFeature.ROAD_FEATURE_PLACED_KEY,
                new PlacedFeature(configuredFeatureRegistryEntryLookup.getOrThrow(RoadFeature.ROAD_FEATURE_KEY),
                        List.of(HeightmapPlacementModifier.of(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES)))
        );
    }
}