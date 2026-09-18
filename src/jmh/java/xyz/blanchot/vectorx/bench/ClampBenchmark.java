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
 * Benchmarks {@code ClampKernels.clampInPlace}, the element-wise loop in real
 * Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction$Sampler}.
 * Clamp is used 6 times in the vanilla noise router ({@code NoiseRouterData}).
 * <p>
 * Since 26.3 the pipeline is {@code float}, not {@code double}, so both
 * backends here work on {@code float[]} and the vector backend gets twice the
 * lanes per register it had in 26.2.
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

    private float[] source;
    private float[] scratch;

    private ClampKernels scalarBackend;
    private ClampKernels vectorBackend;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        source = new float[size];
        for (int i = 0; i < size; i++) {
            source[i] = (float) ((random.nextDouble() - 0.5) * 4.0);
        }
        scratch = new float[size];
        scalarBackend = ScalarClampKernels.INSTANCE;
        vectorBackend = SimdClampKernels.INSTANCE;
    }

    @Benchmark
    public float[] scalarOptimized() {
        System.arraycopy(source, 0, scratch, 0, size);
        scalarBackend.clampInPlace(scratch, size, -1.0F, 1.0F);
        return scratch;
    }

    @Benchmark
    public float[] vector() {
        System.arraycopy(source, 0, scratch, 0, size);
        vectorBackend.clampInPlace(scratch, size, -1.0F, 1.0F);
        return scratch;
    }
}
