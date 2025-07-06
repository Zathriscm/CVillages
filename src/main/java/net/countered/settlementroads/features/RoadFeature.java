package net.countered.settlementroads.features;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.events.ModEventHandler;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.helpers.StructureLocator;
import net.countered.settlementroads.persistence.RoadData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoadFeature extends Feature<RoadFeatureConfig> {

    public static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    // Cache road paths per segment roadId
    public static Map<Integer, Map<BlockPos, Set<BlockPos>>> roadSegmentsCache = new LinkedHashMap<>();
    // Cache road attributes per roadId
    public static Map<Integer, Records.RoadAttributesData> roadAttributesCache = new HashMap<>();
    // Cache chunks where roads will be generated
    public static final Set<ChunkPos> roadChunksCache = ConcurrentHashMap.newKeySet();
    // Villages that need to be added to cache
    public static Set<BlockPos> pendingStructuresToCache = new HashSet<>();
    // Road post-processing positions
    public static Set<BlockPos> roadPostProcessingPositions = ConcurrentHashMap.newKeySet();
    public static Set<Records.RoadDecoration> roadDecorationPlacementPositions = ConcurrentHashMap.newKeySet();

    public static final Set<Block> dontPlaceHere = new HashSet<>();
    static {
        dontPlaceHere.add(Blocks.PACKED_ICE);
        dontPlaceHere.add(Blocks.ICE);
        dontPlaceHere.add(Blocks.BLUE_ICE);
        dontPlaceHere.add(Blocks.TALL_SEAGRASS);
        dontPlaceHere.add(Blocks.MANGROVE_ROOTS);
    }

    public static int chunksForLocatingCounter = 1;

    public static final RegistryKey<PlacedFeature> ROAD_FEATURE_PLACED_KEY =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(SettlementRoads.MOD_ID, "road_feature_placed"));
    public static final RegistryKey<ConfiguredFeature<?,?>> ROAD_FEATURE_KEY =
            RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, Identifier.of(SettlementRoads.MOD_ID, "road_feature"));
    public static final Feature<RoadFeatureConfig> ROAD_FEATURE = new RoadFeature(RoadFeatureConfig.CODEC);
    public RoadFeature(Codec<RoadFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<RoadFeatureConfig> context) {
        ServerWorld serverWorld = context.getWorld().toServerWorld();
        StructureWorldAccess worldAccess = context.getWorld();

        RoadData roadData = ModEventHandler.getRoadData(serverWorld);
        //RoadMath.estimateMemoryUsage();

        if (roadData == null) {
            return false;
        }
        if (roadData.getStructureLocations().size() < 2) {
            return false;
        }
        if (roadData.getStructureLocations().size() < ModConfig.maxLocatingCount && !ModConfig.loadRoadChunks) {
            locateStructureDynamically(serverWorld, 300);
        }

        RoadCaching.cacheDynamicVillages(roadData, context);

        generateRoad(roadData, context);

        RoadStructures.placeDecorations(worldAccess, context);

        return true;
    }

    private void generateRoad(RoadData roadData, FeatureContext<RoadFeatureConfig> context) {
        StructureWorldAccess structureWorldAccess = context.getWorld();
        BlockPos genPos = context.getOrigin();
        ChunkPos currentChunkPos = new ChunkPos(genPos);
        if (roadChunksCache.isEmpty() && !ModEventHandler.stopRecaching) {
            RoadCaching.runCachingLogic(roadData, context);
        }
        if (roadChunksCache.contains(currentChunkPos)){
            runRoadLogic(currentChunkPos, structureWorldAccess);
        }
    }

    private void runRoadLogic(ChunkPos currentChunkPos, StructureWorldAccess structureWorldAccess) {
        int averagingRadius = ModConfig.averagingRadius;

        for (Map.Entry<Integer, Map<BlockPos, Set<BlockPos>>> roadEntry : roadSegmentsCache.entrySet()) {
            int roadId = roadEntry.getKey();
            Records.RoadAttributesData attributes = roadAttributesCache.get(roadId);
            List<BlockState> material = attributes.material();
            int natural = attributes.natural();
            Random deterministicRandom = attributes.deterministicRandom();

            int segmentIndex = 0;
            List<BlockPos> middleBlockPositions = new ArrayList<>(roadEntry.getValue().keySet());

            for (int i = 2; i < middleBlockPositions.size() - 2; i++) {
                BlockPos prevPos = middleBlockPositions.get(i - 2);
                BlockPos currentPos = middleBlockPositions.get(i);
                BlockPos nextPos = middleBlockPositions.get(i + 2);
                List<Integer> heights = new ArrayList<>();
                segmentIndex++;
                if (segmentIndex == 1) continue;

                ChunkPos middleChunkPos = new ChunkPos(currentPos);

                if (currentChunkPos.equals(middleChunkPos)) {

                    for (int j = i - averagingRadius; j <= i + averagingRadius; j++) {
                        if (j >= 0 && j < middleBlockPositions.size()) {
                            BlockPos neighborPos = middleBlockPositions.get(j);
                            int neighborY = structureWorldAccess.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, neighborPos.getX(), neighborPos.getZ());
                            heights.add(neighborY);
                        }
                    }

                    int averageY = (int) Math.round(heights.stream().mapToInt(Integer::intValue).average().orElse(currentPos.getY()));
                    BlockPos averagedPos = new BlockPos(currentPos.getX(), averageY, currentPos.getZ());

                    // first place width blocks
                    for (BlockPos widthBlockPos : roadEntry.getValue().get(currentPos)) {
                        BlockPos correctedYPos = new BlockPos(widthBlockPos.getX(), averageY, widthBlockPos.getZ());
                        placeOnSurface(structureWorldAccess, correctedYPos, material, natural, deterministicRandom, -1, nextPos, prevPos, middleBlockPositions);
                    }
                    // then place middle blocks & decorations
                    placeOnSurface(structureWorldAccess, averagedPos, material, natural, deterministicRandom, segmentIndex, nextPos, prevPos, middleBlockPositions);
                    addDecoration(structureWorldAccess, averagedPos, segmentIndex, nextPos, prevPos, middleBlockPositions);
                }
            }
        }
    }

    private void addDecoration(StructureWorldAccess structureWorldAccess, BlockPos placePos, int segmentIndex, BlockPos nextPos, BlockPos prevPos, List<BlockPos> middleBlockPositions) {
        if (!(segmentIndex == 10 || segmentIndex == middleBlockPositions.size()-10 || segmentIndex % 60 == 0)){
            return;
        }
        // Road vector
        Vec3i directionVector = new Vec3i(
                nextPos.getX() - prevPos.getX(),
                0,
                nextPos.getZ() - prevPos.getZ()
        );
        // orthogonal vector
        Vec3i orthogonalVector = new Vec3i(-directionVector.getZ(), 0, directionVector.getX());
        boolean isStart = segmentIndex != middleBlockPositions.size() - 10;
        BlockPos shiftedPos = isStart
                ? placePos.add(orthogonalVector.multiply(1))
                : placePos.subtract(orthogonalVector.multiply(1));

        roadDecorationPlacementPositions.add(new Records.RoadDecoration(shiftedPos, orthogonalVector, segmentIndex, String.valueOf(middleBlockPositions.size()), isStart));
    }

    private void placeOnSurface(StructureWorldAccess structureWorldAccess, BlockPos placePos, List<BlockState> material, int natural, Random deterministicRandom, int centerBlockCount, BlockPos nextPos, BlockPos prevPos, List<BlockPos> middleBlockPositions) {
        double naturalBlockChance = 0.5;
        BlockPos surfacePos = placePos;
        if (natural == 1) {
            surfacePos = structureWorldAccess.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, placePos);
        }
        BlockState blockStateAtPos = structureWorldAccess.getBlockState(structureWorldAccess.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, surfacePos).down());
        if (blockStateAtPos.equals(Blocks.WATER.getDefaultState())) {
            // If it's water, place a buoy
            if (centerBlockCount % (ModConfig.distanceBetweenBuoys + 6) == 0) {
                RoadStructures.placeBuoy(structureWorldAccess, surfacePos);
            }
        }
        else {
            if (ModConfig.placeWaypoints) {
                if (centerBlockCount % 30 == 0) {
                    RoadStructures.placeWaypointMarker(structureWorldAccess, surfacePos);
                }
                return;
            }
            // place road
            if (natural == 0 || deterministicRandom.nextDouble() < naturalBlockChance) {
                placeRoadBlock(structureWorldAccess, blockStateAtPos, surfacePos, material, deterministicRandom);
                // add road block position to post process
                roadPostProcessingPositions.add(surfacePos.down());
            }
        }
    }

    private void placeRoadBlock(StructureWorldAccess structureWorldAccess, BlockState blockStateAtPos, BlockPos surfacePos, List<BlockState> materials, Random deterministicRandom) {
        // If not water, just place the road
        if (!placeAllowedCheck(blockStateAtPos.getBlock())
                || (!structureWorldAccess.getBlockState(surfacePos.down()).isOpaque()
                && !structureWorldAccess.getBlockState(surfacePos.down(2)).isOpaque()
                && !structureWorldAccess.getBlockState(surfacePos.down(3)).isOpaque())
                || structureWorldAccess.getBlockState(surfacePos.up(3)).isOpaque()
        ){
            return;
        }
        BlockState material = materials.get(deterministicRandom.nextInt(materials.size()));
        setBlockState(structureWorldAccess, surfacePos.down(), material);

        for (int i = 0; i < 4; i++) {
            if (!structureWorldAccess.getBlockState(surfacePos.up(i)).getBlock().equals(Blocks.AIR)) {
                setBlockState(structureWorldAccess, surfacePos.up(i), Blocks.AIR.getDefaultState());
            }
            else {
                break;
            }
        }
        BlockPos belowPos1 = surfacePos.down(2);
        BlockPos belowPos2 = surfacePos.down(3);
        BlockPos belowPos3 = surfacePos.down(4);

        BlockState belowState1 = structureWorldAccess.getBlockState(belowPos1);
        BlockState belowState2 = structureWorldAccess.getBlockState(belowPos2);
        // fill dirt below
        if (structureWorldAccess.getBlockState(belowPos2).isOpaque() || structureWorldAccess.getBlockState(belowPos2).isOf(Blocks.GRASS_BLOCK)
                && !belowState1.isOpaque()) {
            setBlockState(structureWorldAccess, belowPos1, Blocks.DIRT.getDefaultState());
            setBlockState(structureWorldAccess, belowPos2, Blocks.DIRT.getDefaultState());
        }
        else if (structureWorldAccess.getBlockState(belowPos3).isOpaque() || structureWorldAccess.getBlockState(belowPos3).isOf(Blocks.GRASS_BLOCK)
                && !belowState1.isOpaque()
                && !belowState2.isOpaque()
        ) {
            setBlockState(structureWorldAccess, belowPos1, Blocks.DIRT.getDefaultState());
            setBlockState(structureWorldAccess, belowPos2, Blocks.DIRT.getDefaultState());
            setBlockState(structureWorldAccess, belowPos3, Blocks.DIRT.getDefaultState());
        }
    }

    private int placeBridge(Map.Entry<BlockPos, Integer> blockPosEntry, StructureWorldAccess structureWorldAccess) {
        BlockPos placePos = blockPosEntry.getKey();
        BlockPos surfacePos = structureWorldAccess.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, placePos);
        BlockState blockStateAtPos = structureWorldAccess.getBlockState(surfacePos.down());
        if (blockStateAtPos.isOf(Blocks.WATER)) {
            setBlockState(structureWorldAccess, surfacePos, Blocks.OAK_PLANKS.getDefaultState());
            return 0;
        }
        return -1;
    }

    private boolean placeAllowedCheck (Block blockToCheck) {
        return !(dontPlaceHere.contains(blockToCheck)
                || blockToCheck.getDefaultState().isIn(BlockTags.LEAVES)
                || blockToCheck.getDefaultState().isIn(BlockTags.LOGS)
                || blockToCheck.getDefaultState().isIn(BlockTags.UNDERWATER_BONEMEALS)
        );
    }

    private void locateStructureDynamically(ServerWorld serverWorld, int chunksNeeded) {
        if (chunksForLocatingCounter % chunksNeeded != 0){
            chunksForLocatingCounter++;
        }
        else {
            LOGGER.info("Locating structure dynamically");
            try {
                StructureLocator.locateConfiguredStructure(serverWorld, 1, true);
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
            chunksForLocatingCounter = 1;
        }
    }
}

