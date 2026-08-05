package xyz.blanchot.vectorx.bench;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Exploratory prototype (NOT a shipped kernel) validating whether
 * {@code CanyonWorldCarver}'s {@code shouldSkip} geometry test -- the inner
 * Y-loop of {@code WorldCarver.carveEllipsoid} -- is worth vectorizing.
 * {@code xd}/{@code zd} are loop-invariant across the Y sweep for a fixed
 * (x, z) column, so this benchmarks the realistic per-column shape: one
 * {@code (xd, zd)} pair, {@code yLength} values of {@code worldY}.
 * <p>
 * {@code yLength} values come from reading the actual vanilla
 * {@code canyon.json} config (yScale=3.0, thickness trapezoid(0,6,plateau
 * 2), radius factors ~0.75-1.0): typical vertical radius per carve step
 * ranges roughly 4-13 blocks depending on position along the tunnel,
 * giving Y-loop lengths roughly 10-28. The swept values below bracket that
 * range plus the smaller end (matching cave carver's ~5-11 range) for
 * comparison.
 * <p>
 * Run with {@code ./gradlew jmhRun --args="CarverShouldSkipBenchmark"}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CarverShouldSkipBenchmark {

    private static final VectorSpecies<Double> DSPECIES = DoubleVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Float> FSPECIES =
            VectorSpecies.of(float.class, VectorShape.forBitSize(DSPECIES.length() * Float.SIZE));
    private static final DoubleVector LANE_OFFSETS = laneOffsets();
    @Param({"6", "10", "16", "20", "25"})
    public int yLength;
    private double xd;
    private double zd;
    private double verticalRadius;
    private double y;
    private int minY;
    private int maxY;
    private float[] widthFactorPerHeight;
    private boolean[] skipOut;

    private static DoubleVector laneOffsets() {
        double[] offsets = new double[DSPECIES.length()];
        for (int lane = 0; lane < offsets.length; lane++) {
            offsets[lane] = lane;
        }
        return DoubleVector.fromArray(DSPECIES, offsets, 0);
    }

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(7);
        // Inside the horizontal ellipse (xd^2 + zd^2 < 1), as carveEllipsoid
        // guarantees before entering the Y-loop.
        xd = random.nextDouble() * 0.5;
        zd = random.nextDouble() * 0.5;
        verticalRadius = yLength / 2.0 - 1.0;
        y = 64.0;
        minY = (int) Math.floor(y - verticalRadius) - 1;
        maxY = (int) Math.floor(y + verticalRadius) + 1;
        int depth = 400;
        widthFactorPerHeight = new float[depth];
        for (int i = 0; i < depth; i++) {
            widthFactorPerHeight[i] = 0.5f + random.nextFloat();
        }
        skipOut = new boolean[maxY - minY];

        // Correctness check: vector must match scalar before trusting timings.
        boolean[] scalarResult = scalarShouldSkip();
        boolean[] vectorResult = vectorShouldSkip();
        for (int i = 0; i < scalarResult.length; i++) {
            if (scalarResult[i] != vectorResult[i]) {
                throw new IllegalStateException(
                        "scalar/vector mismatch at index " + i + " for yLength=" + yLength);
            }
        }
    }

    /**
     * Same arithmetic as {@code CanyonWorldCarver.shouldSkip}, walked in
     * ascending {@code worldY} order (matching {@link #vectorShouldSkip}) so
     * the two benchmarks' output arrays line up index-for-index -- the set
     * of skip results a real carve call would compute is identical either
     * way, since each worldY's result is independent of iteration order.
     */
    @Benchmark
    public boolean[] scalarShouldSkip() {
        double horizSum = xd * xd + zd * zd;
        int n = maxY - minY;
        for (int i = 0; i < n; i++) {
            int worldY = minY + 1 + i;
            double yd = (worldY - 0.5 - y) / verticalRadius;
            skipOut[i] = horizSum * widthFactorPerHeight[worldY - 1] + yd * yd / 6.0 >= 1.0;
        }
        return skipOut;
    }

    @Benchmark
    public boolean[] vectorShouldSkip() {
        double horizSum = xd * xd + zd * zd;
        int n = maxY - minY;
        int lanes = DSPECIES.length();
        int bound = DSPECIES.loopBound(n);

        // True div, not a precomputed-reciprocal multiply -- matches the
        // shipped SimdCarverSkipKernels exactly, so this benchmark measures
        // the computation that actually ships, not a cheaper stand-in.
        // Ascending worldY order internally (independent per lane; only the
        // *set* of skip results matters, not vanilla's descending iteration
        // order), starting at minY + 1 .. maxY.
        int i = 0;
        for (; i < bound; i += lanes) {
            int worldYBase = minY + 1 + i;
            DoubleVector worldYVec = LANE_OFFSETS.add(worldYBase);
            DoubleVector ydVec = worldYVec.sub(0.5).sub(y).div(verticalRadius);

            FloatVector wfpVec = FloatVector.fromArray(FSPECIES, widthFactorPerHeight, worldYBase - 1);
            DoubleVector wfpVecD = (DoubleVector) wfpVec.convertShape(VectorOperators.F2D, DSPECIES, 0);

            DoubleVector lhs = ydVec.mul(ydVec).div(6.0).add(wfpVecD.mul(horizSum));
            VectorMask<Double> mask = lhs.compare(VectorOperators.GE, 1.0);
            for (int lane = 0; lane < lanes; lane++) {
                skipOut[i + lane] = mask.laneIsSet(lane);
            }
        }
        for (; i < n; i++) {
            int worldY = minY + 1 + i;
            double yd = (worldY - 0.5 - y) / verticalRadius;
            skipOut[i] = horizSum * widthFactorPerHeight[worldY - 1] + yd * yd / 6.0 >= 1.0;
        }
        return skipOut;
    }
}
