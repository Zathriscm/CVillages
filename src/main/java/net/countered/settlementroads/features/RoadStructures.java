package net.countered.settlementroads.features;

import net.countered.settlementroads.helpers.Records;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HangingSignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.Iterator;
import java.util.Objects;

public class RoadStructures {

    public enum DecorationType {
        SIGN, LANTERN
    }

    public static void placeBuoy(StructureWorldAccess worldAccess, BlockPos surfacePos) {
        worldAccess.setBlockState(surfacePos.down(), Blocks.SPRUCE_PLANKS.getDefaultState(), 3);
        worldAccess.setBlockState(surfacePos, Blocks.SPRUCE_FENCE.getDefaultState(), 3);
    }

    public static void placeDecoration(
            StructureWorldAccess structureWorldAccess,
            BlockPos surfacePos,
            Vec3i orthogonalVector,
            int distance,
            boolean isStart,
            DecorationType type,
            String signText
    ) {
        int rotation = getCardinalRotationFromVector(orthogonalVector, isStart);

        Direction offsetDirection;
        Property<Boolean> reverseDirectionProperty;
        Property<Boolean> directionProperty;
        switch (rotation) {
            case 12 -> { offsetDirection = Direction.NORTH; reverseDirectionProperty = Properties.SOUTH; directionProperty = Properties.NORTH; }
            case 0 -> { offsetDirection = Direction.EAST;  reverseDirectionProperty = Properties.WEST;  directionProperty = Properties.EAST; }
            case 4 -> { offsetDirection = Direction.SOUTH; reverseDirectionProperty = Properties.NORTH; directionProperty = Properties.SOUTH; }
            default -> { offsetDirection = Direction.WEST;  reverseDirectionProperty = Properties.EAST;  directionProperty = Properties.WEST; }
        }

        if (type == DecorationType.SIGN) {
            structureWorldAccess.setBlockState(surfacePos.up(2).offset(offsetDirection.getOpposite()), Blocks.SPRUCE_HANGING_SIGN.getDefaultState()
                    .with(Properties.ROTATION, rotation)
                    .with(Properties.ATTACHED, true), 3 );
            updateSigns(structureWorldAccess, surfacePos.up(2).offset(offsetDirection.getOpposite()), signText);
        } else if (type == DecorationType.LANTERN) {
            structureWorldAccess.setBlockState(surfacePos.up(2).offset(offsetDirection.getOpposite()), Blocks.LANTERN.getDefaultState()
                    .with(Properties.HANGING, true), 3);
        }

        structureWorldAccess.setBlockState(surfacePos.up(3).offset(offsetDirection.getOpposite()), Blocks.SPRUCE_FENCE.getDefaultState().with(directionProperty, true), 3);
        structureWorldAccess.setBlockState(surfacePos.up(0), Blocks.SPRUCE_FENCE.getDefaultState(),3);
        structureWorldAccess.setBlockState(surfacePos.up(1), Blocks.SPRUCE_FENCE.getDefaultState(),3);
        structureWorldAccess.setBlockState(surfacePos.up(2), Blocks.SPRUCE_FENCE.getDefaultState(),3);
        structureWorldAccess.setBlockState(surfacePos.up(3), Blocks.SPRUCE_FENCE.getDefaultState().with(reverseDirectionProperty, true), 3);
    }

    public static void placeDecorations(StructureWorldAccess structureWorldAccess, FeatureContext<RoadFeatureConfig> context) {
        if (RoadFeature.roadDecorationPlacementPositions.isEmpty()) {
            return;
        }
        Iterator<Records.RoadDecoration> iterator = RoadFeature.roadDecorationPlacementPositions.iterator();
        while (iterator.hasNext()) {
            Records.RoadDecoration roadDecoration = iterator.next();
            if (roadDecoration != null) {
                BlockPos placePos = roadDecoration.placePos();

                BlockPos surfacePos = placePos.withY(structureWorldAccess.getChunk(placePos).sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, placePos.getX(), placePos.getZ())+1);

                BlockState blockStateBelow = structureWorldAccess.getBlockState(surfacePos.down());
                if (blockStateBelow.isOf(Blocks.WATER) || blockStateBelow.isOf(Blocks.LAVA) || blockStateBelow.isIn(BlockTags.LOGS) || RoadFeature.dontPlaceHere.contains(blockStateBelow.getBlock())) {
                    iterator.remove();
                    continue;
                }
                int centerBlockCount = roadDecoration.centerBlockCount();
                String signText = roadDecoration.signText();
                Vec3i orthogonalVector = roadDecoration.vector();
                boolean isStart = roadDecoration.isStart();
                // place lantern
                if (centerBlockCount % 60 == 0){
                    RoadStructures.placeLantern(structureWorldAccess, surfacePos, orthogonalVector, 1, true);
                }
                // place distance sign
                if (centerBlockCount == 10){
                    RoadStructures.placeDistanceSign(structureWorldAccess, surfacePos, orthogonalVector, 1, true, signText);
                }
                if (!isStart) {
                    RoadStructures.placeDistanceSign(structureWorldAccess, surfacePos, orthogonalVector, 1, false, signText);
                }
                iterator.remove();
            }
        }
    }

    private static void updateSigns(StructureWorldAccess structureWorldAccess, BlockPos surfacePos, String text) {
        Objects.requireNonNull(structureWorldAccess.getServer()).execute( () -> {
            BlockEntity signEntity = structureWorldAccess.getBlockEntity(surfacePos);
            if (signEntity instanceof HangingSignBlockEntity signBlockEntity) {
                signBlockEntity.setWorld(structureWorldAccess.toServerWorld());
                SignText signText = signBlockEntity.getText(true);
                signText = (signText.withMessage(0, Text.literal("----------")));
                signText = (signText.withMessage(1, Text.literal("Next Village")));
                signText = (signText.withMessage(2, Text.literal(text + "m")));
                signText = (signText.withMessage(3, Text.literal("----------")));
                signBlockEntity.setText(signText, true);

                SignText signTextBack = signBlockEntity.getText(false);
                signTextBack = signTextBack.withMessage(0, Text.of("----------"));
                signTextBack = signTextBack.withMessage(1, Text.of("Welcome"));
                signTextBack = signTextBack.withMessage(2, Text.of("traveller"));
                signTextBack = signTextBack.withMessage(3, Text.of("----------"));
                signBlockEntity.setText(signTextBack, false);

                signBlockEntity.markDirty();
            }
        });
    }

    private static int getCardinalRotationFromVector(Vec3i vector, boolean start) {
        if (start) {
            if (Math.abs(vector.getX()) > Math.abs(vector.getZ())) {
                return vector.getX() > 0 ? 0 : 8; // N or S
            } else {
                return vector.getZ() > 0 ? 4 : 12; // E or W
            }
        }
        else {
            if (Math.abs(vector.getX()) > Math.abs(vector.getZ())) {
                return vector.getX() > 0 ? 8 : 0; // N or S
            } else {
                return vector.getZ() > 0 ? 12 : 4; // E or W
            }
        }
    }

    public static void placeDistanceSign(
            StructureWorldAccess structureWorldAccess,
            BlockPos surfacePos,
            Vec3i orthogonalVector,
            int distance,
            boolean isStart,
            String signText
    ) {
        placeDecoration(structureWorldAccess, surfacePos, orthogonalVector, distance, isStart, DecorationType.SIGN, signText);
    }

    public static void placeLantern(
            StructureWorldAccess structureWorldAccess,
            BlockPos surfacePos,
            Vec3i orthogonalVector,
            int distance,
            boolean isStart
    ) {
        placeDecoration(structureWorldAccess, surfacePos, orthogonalVector, distance, isStart, DecorationType.LANTERN, null);
    }

    public static void placeWaypointMarker(StructureWorldAccess worldAccess, BlockPos surfacePos) {
        worldAccess.setBlockState(surfacePos, Blocks.COBBLESTONE_WALL.getDefaultState(), 3);
        worldAccess.setBlockState(surfacePos.up(), Blocks.TORCH.getDefaultState(), 3);
    }

}
