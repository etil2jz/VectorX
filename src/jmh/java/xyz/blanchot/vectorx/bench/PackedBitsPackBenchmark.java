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
import xyz.blanchot.vectorx.kernel.PackedBitsKernels;
import xyz.blanchot.vectorx.kernel.scalar.ScalarPackedBitsKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdPackedBitsKernels;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks {@code PackedBitsKernels.pack}, the inverse of {@code unpack}
 * (benchmarked separately in {@link PackedBitsUnpackBenchmark}). Only
 * {@code bits=4} is vectorized; every other width delegates to the scalar
 * reference.
 * <p>
 * Run with {@code ./gradlew jmhRun --args="PackedBitsPackBenchmark"}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PackedBitsPackBenchmark {

    @Param({"256", "1024", "4096"})
    public int size;

    @Param({"4"})
    public int bits;

    private int[] values;
    private long[] output;

    private PackedBitsKernels scalarBackend;
    private PackedBitsKernels vectorBackend;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(1729);
        long mask = (1L << bits) - 1L;
        values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = (int) (random.nextLong() & mask);
        }

        int valuesPerLong = 64 / bits;
        int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
        output = new long[requiredLongs];
        scalarBackend = ScalarPackedBitsKernels.INSTANCE;
        vectorBackend = SimdPackedBitsKernels.INSTANCE;
    }

    @Benchmark
    public long[] scalarOptimized() {
        scalarBackend.pack(values, bits, size, output);
        return output;
    }

    @Benchmark
    public long[] vector() {
        vectorBackend.pack(values, bits, size, output);
        return output;
    }
}
