package net.countered.settlementroads.events;


import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.features.RoadFeature;
import net.countered.settlementroads.helpers.StructureLocator;
import net.countered.settlementroads.persistence.RoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.countered.settlementroads.SettlementRoads.MOD_ID;

public class ModEventHandler {

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Map<RegistryKey<World>, RoadData> roadDataMap = new ConcurrentHashMap<>();

    public static boolean stopRecaching = false;

    public static void register() {

        ServerWorldEvents.LOAD.register((minecraftServer, serverWorld) -> {
            stopRecaching = false;
            RoadData roadData = getRoadData(serverWorld);
            if (roadData == null) {
                return;
            }
            try {
                if (roadData.getStructureLocations().size() < ModConfig.initialLocatingCount) {
                    StructureLocator.locateConfiguredStructure(serverWorld, ModConfig.initialLocatingCount, false);
                }
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
        });
        ServerWorldEvents.UNLOAD.register((minecraftServer, serverWorld) -> {
            LOGGER.info("Clearing road cache...");
            roadDataMap.clear();
            RoadFeature.roadSegmentsCache.clear();
            RoadFeature.roadAttributesCache.clear();
            RoadFeature.roadChunksCache.clear();
        });
        ServerChunkEvents.CHUNK_GENERATE.register(ModEventHandler::clearRoad);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            server.getWorlds().forEach(serverWorld -> {
                if (getRoadData(serverWorld) == null) {
                    return;
                };
                if (Objects.requireNonNull(getRoadData(serverWorld)).getStructureLocations().isEmpty()) {
                    return;
                };
                if (ModConfig.loadRoadChunks){
                    loadRoadChunksCompletely(serverWorld);
                }
            });
        });
    }

    private static final ChunkTicketType<BlockPos> ROAD_TICKET = ChunkTicketType.create("road_ticket", Comparator.comparingLong(BlockPos::asLong));
    private static final Set<ChunkPos> toRemove = ConcurrentHashMap.newKeySet();
    private static final int MAX_BLOCKS_PER_TICK = 1;

    private static void loadRoadChunksCompletely(ServerWorld serverWorld) {
        if (!RoadFeature.roadChunksCache.isEmpty()) {
            stopRecaching = true;
            RoadFeature.roadChunksCache.removeIf(chunkPos -> serverWorld.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FEATURES, true) != null);
        }
    }

    private static void clearRoad(ServerWorld serverWorld, WorldChunk worldChunk) {
        if (RoadFeature.roadPostProcessingPositions.isEmpty()) {
            return;
        }
        for (BlockPos postProcessingPos : RoadFeature.roadPostProcessingPositions) {
            if (postProcessingPos != null) {
                Block blockAbove = worldChunk.getBlockState(postProcessingPos.up()).getBlock();
                Block blockAtPos = worldChunk.getBlockState(postProcessingPos).getBlock();
                if (blockAbove == Blocks.SNOW) {
                    worldChunk.setBlockState(postProcessingPos.up(), Blocks.AIR.getDefaultState(), false);
                    if (blockAtPos == Blocks.GRASS_BLOCK) {
                        worldChunk.setBlockState(postProcessingPos, Blocks.GRASS_BLOCK.getDefaultState(), false);
                    }
                }
                RoadFeature.roadPostProcessingPositions.remove(postProcessingPos);
            }
        }
    }

    public static RoadData getRoadData(ServerWorld serverWorld) {
        if (serverWorld.getDimension().hasCeiling()) {
            return null;
        }
        return roadDataMap.computeIfAbsent(serverWorld.getRegistryKey(),
                key -> RoadData.getOrCreateRoadData(serverWorld));
    }
}
