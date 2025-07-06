package net.countered.settlementroads.features;

import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.persistence.RoadData;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.*;

public class RoadCaching {

    public static void runCachingLogic(RoadData roadData, FeatureContext<RoadFeatureConfig> context) {
        List<BlockPos> villages = roadData.getStructureLocations();
        Map<BlockPos, BlockPos> closestVillageMap = new HashMap<>();

        for (BlockPos village : villages) {
            BlockPos closestVillage = findClosestVillage(village, villages);
            if (closestVillage != null) {
                closestVillageMap.put(village, closestVillage);
            }
        }
        // Generate roads for each village to its closest village
        for (Map.Entry<BlockPos, BlockPos> entry : closestVillageMap.entrySet()) {
            BlockPos start = entry.getKey();
            BlockPos end = entry.getValue();
            // Generate a unique road identifier for the current road segment
            int roadId = calculateRoadId(start, end);
            Random deterministicRandom = Random.create(roadId);
            int width = getRandomWidth(deterministicRandom, context);

            int type = allowedRoadTypes(deterministicRandom);
            if (type == -1) {
                continue;
            }
            List<BlockState> material = (type == 1) ? getRandomNaturalRoadMaterials(deterministicRandom, context) : getRandomArtificialRoadMaterials(deterministicRandom, context);

            // Calculate a determined path
            List<BlockPos> waypoints = RoadMath.generateControlPoints(start, end, deterministicRandom);
            Map<BlockPos, Set<BlockPos>> roadPath = RoadMath.calculateSplinePath(waypoints, width);

            RoadFeature.roadAttributesCache.put(roadId, new Records.RoadAttributesData(width, type, material, deterministicRandom));
            RoadFeature.roadSegmentsCache.put(roadId, roadPath);
        }
    }

    public static void addNewVillageToCache(BlockPos newVillage, RoadData roadData, FeatureContext<RoadFeatureConfig> context) {
        List<BlockPos> existingVillages = roadData.getStructureLocations();

        // Find the closest existing village to the new village
        BlockPos closestVillage = findClosestVillage(newVillage, existingVillages);

        if (closestVillage == null) {
            return; // No existing villages to connect to
        }

        // Generate a unique road identifier
        int roadId = calculateRoadId(newVillage, closestVillage);
        Random deterministicRandom = Random.create(roadId);

        int width = getRandomWidth(deterministicRandom, context);
        int type = allowedRoadTypes(deterministicRandom);
        if (type == -1) {
            return; // No valid road type, skip
        }
        List<BlockState> material = (type == 1) ? getRandomNaturalRoadMaterials(deterministicRandom, context) : getRandomArtificialRoadMaterials(deterministicRandom, context);

        // Generate road path
        List<BlockPos> waypoints = RoadMath.generateControlPoints(newVillage, closestVillage, deterministicRandom);
        Map<BlockPos, Set<BlockPos>> roadPath = RoadMath.calculateSplinePath(waypoints, width);

        // Update cache with the new road
        RoadFeature.roadAttributesCache.put(roadId, new Records.RoadAttributesData(width, type, material, deterministicRandom));
        RoadFeature.roadSegmentsCache.put(roadId, roadPath);
    }

    public static void cacheDynamicVillages(RoadData roadData, FeatureContext<RoadFeatureConfig> context) {
        if (!RoadFeature.pendingStructuresToCache.isEmpty()) {
            Iterator<BlockPos> iterator = RoadFeature.pendingStructuresToCache.iterator();

            while (iterator.hasNext()) {
                BlockPos villagePos = iterator.next();
                RoadCaching.addNewVillageToCache(villagePos, roadData, context);
                iterator.remove(); // Remove from the Set after caching
            }
        }
    }

    private static BlockPos findClosestVillage(BlockPos currentVillage, List<BlockPos> allVillages) {
        BlockPos closestVillage = null;
        double minDistance = Double.MAX_VALUE;

        for (BlockPos village : allVillages) {
            if (!village.equals(currentVillage)) {
                double distance = currentVillage.getSquaredDistance(village);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestVillage = village;
                }
            }
        }
        return closestVillage;
    }

    private static int allowedRoadTypes(Random deterministicRandom) {
        if (ModConfig.allowArtificial && ModConfig.allowNatural){
            return getRandomRoadType(deterministicRandom);
        }
        else if (ModConfig.allowArtificial){
            return 0;
        }
        else if (ModConfig.allowNatural) {
            return 1;
        }
        else {
            return -1;
        }
    }

    private static int getRandomRoadType(Random deterministicRandom) {
        return deterministicRandom.nextBetween(0, 1);
    }

    private static int calculateRoadId(BlockPos start, BlockPos end) {
        return start.hashCode() ^ end.hashCode();
    }

    private static List<BlockState> getRandomNaturalRoadMaterials(Random deterministicRandom, FeatureContext<RoadFeatureConfig> context) {
        List<List<BlockState>> materialsList = context.getConfig().getNaturalMaterials();
        return materialsList.get(deterministicRandom.nextInt(materialsList.size()));
    }

    private static List<BlockState> getRandomArtificialRoadMaterials(Random deterministicRandom, FeatureContext<RoadFeatureConfig> context) {
        List<List<BlockState>> materialsList = context.getConfig().getArtificialMaterials();
        return materialsList.get(deterministicRandom.nextInt(materialsList.size()));
    }

    private static int getRandomWidth(Random deterministicRandom, FeatureContext<RoadFeatureConfig> context) {
        List<Integer> widthList = context.getConfig().getWidths();
        return widthList.get(deterministicRandom.nextInt(widthList.size()));
    }
}
