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
import xyz.blanchot.vectorx.kernel.simd.SimdPackedBitsKernels;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Benchmarks the shape used by {@code SimpleBitStorage.getAll(IntConsumer)}
 * (reached from {@code PalettedContainer.count()}, hit on every chunk-section
 * load once its palette holds more than one entry): Mojang's inline
 * mask/shift-then-dispatch loop against unpacking through the
 * already-vectorized {@code PackedBitsKernels.unpack} kernel into a scratch
 * {@code int[]} and then dispatching from that array.
 * <p>
 * Both variants call the same {@code IntConsumer} once per element in the
 * same order -- the only difference under test is how the value reaches that
 * call, matching exactly what a {@code getAll} Mixin would change and
 * nothing else.
 * <p>
 * Run with {@code ./gradlew jmhRun --args="PackedBitsGetAllBenchmark"}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PackedBitsGetAllBenchmark {

    @Param({"64", "256", "1024", "4096"})
    public int size;

    @Param({"4", "5", "6", "7", "8", "15", "16", "32"})
    public int bits;

    private long[] data;
    private int[] scratch;

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

        scratch = new int[size];
        vectorBackend = SimdPackedBitsKernels.INSTANCE;
    }

    /** Verbatim algorithm from {@code SimpleBitStorage.getAll(IntConsumer)}. */
    private void vanillaGetAll(IntConsumer output) {
        long mask = (1L << bits) - 1L;
        int valuesPerLong = 64 / bits;
        int count = 0;
        for (long cellValue : data) {
            for (int value = 0; value < valuesPerLong; value++) {
                output.accept((int) (cellValue & mask));
                cellValue >>= bits;
                if (++count >= size) {
                    return;
                }
            }
        }
    }

    private void vectorUnpackThenIterate(IntConsumer output) {
        vectorBackend.unpack(data, bits, size, scratch);
        for (int i = 0; i < size; i++) {
            output.accept(scratch[i]);
        }
    }

    @Benchmark
    public long scalarInlineGetAll() {
        long[] acc = {0L};
        vanillaGetAll(v -> acc[0] += v);
        return acc[0];
    }

    @Benchmark
    public long vectorUnpackThenIterate() {
        long[] acc = {0L};
        vectorUnpackThenIterate(v -> acc[0] += v);
        return acc[0];
    }
}
