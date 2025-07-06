package net.countered.settlementroads.helpers;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;

import java.util.List;

public class Records {

    public record RoadAttributesData(int width, int natural, List<BlockState> material, Random deterministicRandom) {}
    public record RoadDecoration(BlockPos placePos, Vec3i vector, int centerBlockCount, String signText, boolean isStart) {}
}
