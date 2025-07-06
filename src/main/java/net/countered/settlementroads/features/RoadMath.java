package net.countered.settlementroads.features;

import net.countered.settlementroads.SettlementRoads;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RoadMath {

    private static final Set<BlockPos> widthPositionsCache = new HashSet<>();
    public static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static Map<BlockPos, Set<BlockPos>> calculateSplinePath(List<BlockPos> controlPoints, int width) {
        Map<BlockPos, Set<BlockPos>> roadSegments = new LinkedHashMap<>(); // Has to be linked
        Set<BlockPos> middlePositions = new HashSet<>();  // Track middle blocks

        for (int i = 0; i < controlPoints.size() - 1; i++) {
            BlockPos p0 = controlPoints.get(Math.max(0, i - 1));
            BlockPos p1 = controlPoints.get(i);
            BlockPos p2 = controlPoints.get(i + 1);
            BlockPos p3 = controlPoints.get(Math.min(controlPoints.size() - 1, i + 2));

            BlockPos lastSplinePos = null;

            for (double t = 0; t <= 1.0; t += 0.1) {  // Use fine-grained t increments
                BlockPos nextSplinePos = calculateSplinePosition(p0, p1, p2, p3, t);

                if (lastSplinePos != null) {
                    // Use Bresenham to ensure continuous placement
                    List<BlockPos> linePositions = getStraightLine(lastSplinePos, nextSplinePos);
                    for (BlockPos middlePos : linePositions) {
                        if (!middlePositions.contains(middlePos)) {
                            middlePositions.add(middlePos);
                            RoadFeature.roadChunksCache.add(new ChunkPos(middlePos));

                            // Generate road width
                            roadSegments.put(middlePos, generateWidth(middlePos, lastSplinePos, nextSplinePos, width));
                        }
                        else {
                            roadSegments.get(middlePos).addAll(generateWidth(middlePos, lastSplinePos, nextSplinePos, width));
                        }
                    }
                }
                lastSplinePos = nextSplinePos;
            }
        }
        Map<BlockPos, Set<BlockPos>> toRemove = new HashMap<>();
        for (BlockPos middlePos : roadSegments.keySet()) {
            Set<BlockPos> testWidthList = new HashSet<>();
            for (BlockPos widthPos : roadSegments.get(middlePos)) {
                if (middlePositions.contains(widthPos)) {
                    testWidthList.add(widthPos);
                }
            }
            toRemove.put(middlePos, testWidthList);
        }
        for (BlockPos middlePos : toRemove.keySet()) {
            for (BlockPos widthPos : toRemove.get(middlePos)) {
                roadSegments.get(middlePos).remove(widthPos);
            }
        }
        widthPositionsCache.clear();
        return roadSegments;
    }

    private static Set<BlockPos> generateWidth(BlockPos center, BlockPos prev, BlockPos next, int width) {
        Set<BlockPos> segmentWidthPositions = new HashSet<>();
        double adjustedWidth = width-0.05d;
        // Calculate tangent vector (direction of the road)
        int dx = next.getX() - prev.getX();
        int dz = next.getZ() - prev.getZ();
        // Normalize the tangent to get the perpendicular vector
        double length = Math.sqrt(dx * dx + dz * dz);

        double tangentX = dx / length;  // Tangent x
        double tangentZ = dz / length;  // Tangent z
        // Perpendicular vector (normal to the tangent)
        double px = -tangentZ;  // Perpendicular x
        double pz = tangentX;   // Perpendicular z

        // Create road width using perpendicular vector
        List<BlockPos> widthLine = getStraightLine(
                new BlockPos(center.getX() - (int) Math.round(px * (adjustedWidth / 2d)), center.getY(), center.getZ() - (int) Math.round(pz * (adjustedWidth / 2d))),
                new BlockPos(center.getX() + (int) Math.round(px * (adjustedWidth / 2d)), center.getY(), center.getZ() + (int) Math.round(pz * (adjustedWidth / 2d)))
        );
        for (BlockPos widthBlockPos : widthLine) {
            if (widthPositionsCache.contains(widthBlockPos)) {
                continue;
            }
            widthPositionsCache.add(widthBlockPos);
            segmentWidthPositions.add(widthBlockPos);
            RoadFeature.roadChunksCache.add(new ChunkPos(widthBlockPos));
        }
        return segmentWidthPositions;
    }

    public static BlockPos calculateSplinePosition(BlockPos p0, BlockPos p1, BlockPos p2, BlockPos p3, double t) {
        double x = 0.5 * ((2 * p1.getX()) +
                (-p0.getX() + p2.getX()) * t +
                (2 * p0.getX() - 5 * p1.getX() + 4 * p2.getX() - p3.getX()) * t * t +
                (-p0.getX() + 3 * p1.getX() - 3 * p2.getX() + p3.getX()) * t * t * t);

        double z = 0.5 * ((2 * p1.getZ()) +
                (-p0.getZ() + p2.getZ()) * t +
                (2 * p0.getZ() - 5 * p1.getZ() + 4 * p2.getZ() - p3.getZ()) * t * t +
                (-p0.getZ() + 3 * p1.getZ() - 3 * p2.getZ() + p3.getZ()) * t * t * t);

        return new BlockPos((int) Math.round(x), 0, (int) Math.round(z));
    }

    public static List<BlockPos> getStraightLine(BlockPos start, BlockPos end) {
        List<BlockPos> lineBlocks = new ArrayList<>();

        int x1 = start.getX(), z1 = start.getZ();
        int x2 = end.getX(), z2 = end.getZ();

        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1;
        int sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;

        while (true) {
            lineBlocks.add(new BlockPos(x1, 0, z1));

            if (x1 == x2 && z1 == z2) break;
            int e2 = err * 2;

            if (e2 > -dz) {
                err -= dz;
                x1 += sx;
            } else if (e2 < dx) {
                err += dx;
                z1 += sz;
            }
        }
        return lineBlocks;
    }


    public static List<BlockPos> generateControlPoints(BlockPos start, BlockPos end, Random random) {
        List<BlockPos> controlPoints = new ArrayList<>();
        // Calculate vectors from start to end
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        // Distance to end positions
        double scale = 60 / distance;

        double offsetX = dx * scale;
        double offsetZ = dz * scale;

        // Offset the start and end points by 60 blocks
        BlockPos adjustedStart = new BlockPos((int) (start.getX() + offsetX), start.getY(), (int) (start.getZ() + offsetZ));
        BlockPos adjustedEnd = new BlockPos((int) (end.getX() - offsetX), end.getY(), (int) (end.getZ() - offsetZ));

        controlPoints.add(adjustedStart);

        int step = 100;
        int deviationBound = 50;
        // Compute normalized direction vector
        double dirX = dx / distance;
        double dirZ = dz / distance;
        // Compute perpendicular vector (rotate by 90 degrees)
        double perpX = -dirZ;
        double perpZ = dirX;
        // Generate control points at fixed step distances
        for (double d = step*2; d < distance - step*2; d += step) {
            int x = (int) (adjustedStart.getX() + dirX * d);
            int z = (int) (adjustedStart.getZ() + dirZ * d);
            // Apply perpendicular offset randomly
            double deviation = random.nextBetween(20, deviationBound) * (random.nextBoolean() ? 1 : -1);
            x += (int) Math.round(perpX * deviation);
            z += (int) Math.round(perpZ * deviation);

            controlPoints.add(new BlockPos(x, 0, z));
        }
        controlPoints.add(adjustedEnd);
        return controlPoints;
    }

    // debugging
    public static void estimateMemoryUsage() {

        for (Map.Entry<Integer, Map<BlockPos, Set<BlockPos>>> entry : RoadFeature.roadSegmentsCache.entrySet()) {
            LOGGER.info("entry " + entry.getValue().size());
        }

        LOGGER.info("chunkscache"+RoadFeature.roadChunksCache.size());
        LOGGER.info("roadsegmentscache"+RoadFeature.roadSegmentsCache.size());
        LOGGER.info("roadattributescache"+RoadFeature.roadAttributesCache.size());
    }
}
