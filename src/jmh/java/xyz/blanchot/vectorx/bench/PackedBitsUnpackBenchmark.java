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
 * Benchmarks {@code PackedBitsKernels.unpack} across the bit widths
 * vectorized in {@code SimdPackedBitsKernels}.
 * <p>
 * Run with {@code ./gradlew jmhRun --args="PackedBitsUnpackBenchmark"}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PackedBitsUnpackBenchmark {

    @Param({"256", "1024", "4096"})
    public int size;

    @Param({"4", "5", "6", "7", "8", "15", "16", "32"})
    public int bits;

    private long[] data;
    private int[] output;

    private PackedBitsKernels scalarBackend;
    private PackedBitsKernels vectorBackend;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        int valuesPerLong = 64 / bits;
        int requiredLongs = (size + valuesPerLong - 1) / valuesPerLong;
        data = new long[requiredLongs];
        for (int i = 0; i < data.length; i++) {
            data[i] = random.nextLong();
        }
        int written = (data.length - 1) * valuesPerLong;
        int remaining = size - written;
        if (data.length > 0 && remaining > 0 && remaining < valuesPerLong) {
            long validBits = (long) remaining * bits;
            long tailMask = validBits >= 64 ? -1L : (1L << validBits) - 1L;
            data[data.length - 1] &= tailMask;
        }

        output = new int[size];
        scalarBackend = ScalarPackedBitsKernels.INSTANCE;
        vectorBackend = SimdPackedBitsKernels.INSTANCE;
    }

    @Benchmark
    public int[] scalarOptimized() {
        scalarBackend.unpack(data, bits, size, output);
        return output;
    }

    @Benchmark
    public int[] vector() {
        vectorBackend.unpack(data, bits, size, output);
        return output;
    }
}
