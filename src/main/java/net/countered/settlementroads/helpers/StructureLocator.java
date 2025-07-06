package net.countered.settlementroads.helpers;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.events.ModEventHandler;
import net.countered.settlementroads.features.RoadFeature;
import net.minecraft.command.argument.RegistryPredicateArgumentType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class StructureLocator {

    public static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static void locateConfiguredStructure(ServerWorld serverWorld, int locateCount, boolean locateAtPlayer) throws CommandSyntaxException {
        LOGGER.info("Locating " + locateCount + " " + ModConfig.structureToLocate);
        try {
            // Try as key first
            locateStructures(serverWorld, ModConfig.structureToLocate, locateCount, false, locateAtPlayer);
        } catch (IllegalArgumentException eKey) {
            try {
                // If key parsing fails, try as tag
                locateStructures(serverWorld, ModConfig.structureToLocate, locateCount, true, locateAtPlayer);
            } catch (Exception eTag) {
                LOGGER.error("Failed to locate structure as both key and tag: " + locateCount, eTag);
            }
        }
    }

    private static void locateStructures(ServerWorld serverWorld, String structureId, int locateCount, Boolean isTag, boolean locateAtPlayer) throws CommandSyntaxException {
        if (isTag) {
            TagKey<Structure> structureTag = TagKey.of(RegistryKeys.STRUCTURE, Identifier.of(structureId));
            for (int x = 0; x < locateCount; x++) {
                serverWorld.getServer().execute(() -> {
                    if (locateAtPlayer) {
                        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                            addTagStructurePersistent(serverWorld, structureTag, player);
                        }
                    }
                    else {
                        addTagStructurePersistent(serverWorld, structureTag, null);
                    }
                });
            }
        }
        else {
            StringReader reader = new StringReader(structureId);

            RegistryKey<Registry<Structure>> structureRegistryKey = RegistryKeys.STRUCTURE;
            RegistryPredicateArgumentType.RegistryPredicate<Structure> predicate =
                    new RegistryPredicateArgumentType<>(structureRegistryKey).parse(reader);

            Registry<Structure> registry = serverWorld.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);

            RegistryEntryList<Structure> registryEntryList = (RegistryEntryList<Structure>) getStructureListForPredicate(predicate, registry)
                    .orElseThrow(() -> new IllegalArgumentException("Structure not found for identifier: " + structureId));

            for (int x = 0; x < locateCount; x++) {
                serverWorld.getServer().execute(() -> {
                    if (locateAtPlayer) {
                        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                            addKeyStructurePersistent(serverWorld, registryEntryList, player);
                        }
                    }
                    else{
                        addKeyStructurePersistent(serverWorld, registryEntryList, null);
                    }
                });
            }
        }
    }

    private static void addTagStructurePersistent(ServerWorld serverWorld, TagKey<Structure> structureTag, @Nullable ServerPlayerEntity player) {
        BlockPos structureLocation = player != null ? serverWorld.locateStructure(structureTag, player.getBlockPos(), 50, true) :
                                     serverWorld.locateStructure(structureTag, serverWorld.getSpawnPos(), 50, true);
        if (structureLocation != null) {
            LOGGER.info(ModConfig.structureToLocate + " found at " + "/tp " + structureLocation.getX() + " " + 100 + " " + structureLocation.getZ());
            ModEventHandler.getRoadData(serverWorld).getStructureLocations().add(structureLocation);
            ModEventHandler.getRoadData(serverWorld).markDirty();
            // Add new village position to pending for cache
            RoadFeature.pendingStructuresToCache.add(structureLocation);
        }
    }

    private static void addKeyStructurePersistent(ServerWorld serverWorld, RegistryEntryList<Structure> registryEntryList, @Nullable ServerPlayerEntity player) {
        Pair<BlockPos, RegistryEntry<Structure>> structureLocation = player != null ? serverWorld.getChunkManager()
                .getChunkGenerator()
                .locateStructure(serverWorld, registryEntryList, player.getBlockPos(), 50, true):
                serverWorld.getChunkManager()
                        .getChunkGenerator()
                        .locateStructure(serverWorld, registryEntryList, serverWorld.getSpawnPos(), 50, true);
        if (structureLocation != null) {
            LOGGER.info("Structure found at " + structureLocation);
            ModEventHandler.getRoadData(serverWorld).getStructureLocations().add(structureLocation.getFirst());
            ModEventHandler.getRoadData(serverWorld).markDirty();
            // Add new village position to pending for cache
            RoadFeature.pendingStructuresToCache.add(structureLocation.getFirst());
        }
    }

    private static Optional<? extends RegistryEntryList.ListBacked<Structure>> getStructureListForPredicate(
            RegistryPredicateArgumentType.RegistryPredicate<Structure> predicate, Registry<Structure> structureRegistry
    ) {
        return predicate.getKey().map(key -> structureRegistry.getOptional(key).map(entry -> RegistryEntryList.of(entry)), structureRegistry::getOptional);
    }
}
