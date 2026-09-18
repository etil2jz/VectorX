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
import xyz.blanchot.vectorx.kernel.DensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.DensityBinaryOp;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdDensityBinaryKernels;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks {@link DensityBinaryKernels} -- the element-wise loops in real
 * Minecraft 26.3's {@code BinaryFunction$*Sampler} records, by far the most
 * frequently declared density-function ops in vanilla's own worldgen data
 * ({@code add} and {@code mul} alone account for 126 of roughly 200
 * element-wise declarations across the 55 files in
 * {@code data/minecraft/worldgen/density_function/}).
 *
 * <p>Sizes are taken from what 26.3 actually allocates, not guessed:
 * {@code NoiseBasedChunkGenerator.chunkVolume} builds a
 * {@code DensityVolume(16, noiseSettings.height(), 16)}, so the whole-chunk
 * buffer is {@code 16 * 384 * 16 = 98304} floats for a default overworld.
 * {@code MaterialSystem} narrows that to the filled height, and
 * {@code InterpolatedFunction} works on smaller cell volumes, hence the
 * spread below rather than a single size.
 *
 * <p>Run with {@code ./gradlew jmhRun --args="DensityBinaryBenchmark"}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DensityBinaryBenchmark {

    @Param({"128", "4096", "98304"})
    public int size;

    @Param({"ADD", "MIN", "MAX"})
    public DensityBinaryOp op;

    private float[] source;
    private float[] right;
    private float[] scratch;

    private DensityBinaryKernels scalarBackend;
    private DensityBinaryKernels vectorBackend;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        source = new float[size];
        right = new float[size];
        for (int i = 0; i < size; i++) {
            source[i] = (float) ((random.nextDouble() - 0.5) * 4.0);
            right[i] = (float) ((random.nextDouble() - 0.5) * 4.0);
        }
        scratch = new float[size];
        scalarBackend = ScalarDensityBinaryKernels.INSTANCE;
        vectorBackend = SimdDensityBinaryKernels.INSTANCE;
    }

    /**
     * Baseline: the {@code System.arraycopy} every benchmark below performs to
     * restore the input. At 98304 floats that copy costs microseconds on its
     * own, enough to dominate the cheap branch-free ops, so a variant that
     * does not clearly beat this number is measuring memory bandwidth rather
     * than arithmetic.
     */
    @Benchmark
    public float[] copyOnly() {
        System.arraycopy(source, 0, scratch, 0, size);
        return scratch;
    }

    @Benchmark
    public float[] scalarBuffer() {
        System.arraycopy(source, 0, scratch, 0, size);
        scalarBackend.applyBuffer(scratch, right, size, op);
        return scratch;
    }

    @Benchmark
    public float[] vectorBuffer() {
        System.arraycopy(source, 0, scratch, 0, size);
        vectorBackend.applyBuffer(scratch, right, size, op);
        return scratch;
    }

    @Benchmark
    public float[] scalarConst() {
        System.arraycopy(source, 0, scratch, 0, size);
        scalarBackend.applyConst(scratch, size, op, 0.37F);
        return scratch;
    }

    @Benchmark
    public float[] vectorConst() {
        System.arraycopy(source, 0, scratch, 0, size);
        vectorBackend.applyConst(scratch, size, op, 0.37F);
        return scratch;
    }
}
