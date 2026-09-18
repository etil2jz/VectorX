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
import xyz.blanchot.vectorx.kernel.DensitySelectKernels;
import xyz.blanchot.vectorx.kernel.scalar.ScalarDensitySelectKernels;
import xyz.blanchot.vectorx.kernel.simd.SimdDensitySelectKernels;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DensitySelectBenchmark {

    @Param({"128", "4096", "98304"})
    public int size;

    private float[] source;
    private float[] first;
    private float[] second;
    private float[] scratch;

    private DensitySelectKernels scalarBackend;
    private DensitySelectKernels vectorBackend;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        source = new float[size];
        first = new float[size];
        second = new float[size];
        for (int i = 0; i < size; i++) {
            int bucket = random.nextInt(10);
            source[i] = bucket == 0 ? 0.0F : bucket == 1 ? 1.0F : (float) random.nextDouble();
            first[i] = (float) ((random.nextDouble() - 0.5) * 4.0);
            second[i] = (float) ((random.nextDouble() - 0.5) * 4.0);
        }
        scratch = new float[size];
        scalarBackend = ScalarDensitySelectKernels.INSTANCE;
        vectorBackend = SimdDensitySelectKernels.INSTANCE;
    }

    @Benchmark
    public float[] copyOnly() {
        System.arraycopy(source, 0, scratch, 0, size);
        return scratch;
    }

    @Benchmark
    public float[] scalarLerp() {
        System.arraycopy(source, 0, scratch, 0, size);
        scalarBackend.lerp(scratch, first, second, size);
        return scratch;
    }

    @Benchmark
    public float[] vectorLerp() {
        System.arraycopy(source, 0, scratch, 0, size);
        vectorBackend.lerp(scratch, first, second, size);
        return scratch;
    }

    @Benchmark
    public float[] scalarRangeChoiceConst() {
        System.arraycopy(source, 0, scratch, 0, size);
        scalarBackend.rangeChoiceConst(scratch, size, 0.2F, 0.8F, 1.0F, -1.0F);
        return scratch;
    }

    @Benchmark
    public float[] vectorRangeChoiceConst() {
        System.arraycopy(source, 0, scratch, 0, size);
        vectorBackend.rangeChoiceConst(scratch, size, 0.2F, 0.8F, 1.0F, -1.0F);
        return scratch;
    }

    @Benchmark
    public float[] scalarRangeChoice() {
        System.arraycopy(source, 0, scratch, 0, size);
        scalarBackend.rangeChoice(scratch, first, second, size, 0.2F, 0.8F);
        return scratch;
    }

    @Benchmark
    public float[] vectorRangeChoice() {
        System.arraycopy(source, 0, scratch, 0, size);
        vectorBackend.rangeChoice(scratch, first, second, size, 0.2F, 0.8F);
        return scratch;
    }
}
