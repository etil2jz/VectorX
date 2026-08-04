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
import xyz.blanchot.vectorx.kernel.ClampKernels;
import xyz.blanchot.vectorx.kernel.scalar.ScalarClampKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdClampKernels;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks {@code ClampKernels.clampInPlace}, real Minecraft's
 * {@code DensityFunctions.Clamp}, used 6 times in the vanilla noise router
 * ({@code NoiseRouterData.java}).
 * <p>
 * Run with {@code ./gradlew jmhRun --args="ClampBenchmark"}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ClampBenchmark {

    @Param({"32", "97", "128", "4096"})
    public int size;

    private double[] source;
    private double[] scratch;

    private ClampKernels scalarBackend;
    private ClampKernels vectorBackend;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        source = new double[size];
        for (int i = 0; i < size; i++) {
            source[i] = (random.nextDouble() - 0.5) * 4.0;
        }
        scratch = new double[size];
        scalarBackend = ScalarClampKernels.INSTANCE;
        vectorBackend = SimdClampKernels.INSTANCE;
    }

    @Benchmark
    public double[] scalarOptimized() {
        System.arraycopy(source, 0, scratch, 0, size);
        scalarBackend.clampInPlace(scratch, -1.0, 1.0);
        return scratch;
    }

    @Benchmark
    public double[] vector() {
        System.arraycopy(source, 0, scratch, 0, size);
        vectorBackend.clampInPlace(scratch, -1.0, 1.0);
        return scratch;
    }
}
