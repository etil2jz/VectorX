package xyz.blanchot.vectorx.bench;

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
import xyz.blanchot.vectorx.kernel.DensityMapKernels;
import xyz.blanchot.vectorx.kernel.DensityMapOp;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityMapKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdDensityMapKernels;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks {@code DensityMapKernels.apply} (real Minecraft's
 * {@code DensityFunctions.Mapped} transforms) at the array sizes
 * {@code NoiseChunk} actually fills during chunk generation:
 * {@code NoiseInterpolator} slices are {@code cellCountY+1} long, typically
 * in the tens; {@code CacheAllInCell} value arrays are
 * {@code cellWidth * cellWidth * cellHeight}, typically ~128 for vanilla
 * Overworld settings.
 * <p>
 * Run with {@code ./gradlew jmhRun --args="DensityMapBenchmark"}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DensityMapBenchmark {

    @Param({"32", "97", "128", "4096"})
    public int size;

    @Param({"ABS", "SQUARE", "CUBE", "HALF_NEGATIVE", "QUARTER_NEGATIVE", "INVERT", "SQUEEZE"})
    public DensityMapOp op;

    private double[] source;
    private double[] scratch;

    private DensityMapKernels scalarBackend;
    private DensityMapKernels vectorBackend;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        source = new double[size];
        for (int i = 0; i < size; i++) {
            source[i] = (random.nextDouble() - 0.5) * 4.0;
        }
        scratch = new double[size];
        scalarBackend = ScalarDensityMapKernels.INSTANCE;
        vectorBackend = SimdDensityMapKernels.INSTANCE;
    }

    @Benchmark
    public double[] scalarOptimized() {
        System.arraycopy(source, 0, scratch, 0, size);
        scalarBackend.apply(scratch, op);
        return scratch;
    }

    @Benchmark
    public double[] vector() {
        System.arraycopy(source, 0, scratch, 0, size);
        vectorBackend.apply(scratch, op);
        return scratch;
    }
}
