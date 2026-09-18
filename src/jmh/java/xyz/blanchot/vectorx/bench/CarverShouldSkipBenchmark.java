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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CarverShouldSkipBenchmark {

    private static final VectorSpecies<Double> DSPECIES = DoubleVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Float> FSPECIES = VectorSpecies.of(float.class, VectorShape.forBitSize(DSPECIES.length() * Float.SIZE));
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

        boolean[] scalarResult = scalarShouldSkip();
        boolean[] vectorResult = vectorShouldSkip();
        for (int i = 0; i < scalarResult.length; i++) {
            if (scalarResult[i] != vectorResult[i]) {
                throw new IllegalStateException(
                        "scalar/vector mismatch at index " + i + " for yLength=" + yLength);
            }
        }
    }

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
