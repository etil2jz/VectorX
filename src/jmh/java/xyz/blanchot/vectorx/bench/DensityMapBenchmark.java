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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DensityMapBenchmark {

    @Param({"128", "4096", "98304"})
    public int size;

    @Param({"ABS", "SQUARE", "CUBE", "HALF_NEGATIVE", "QUARTER_NEGATIVE", "RECIPROCAL", "SQUEEZE"})
    public DensityMapOp op;

    private float[] source;
    private float[] scratch;

    private DensityMapKernels scalarBackend;
    private DensityMapKernels vectorBackend;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        source = new float[size];
        for (int i = 0; i < size; i++) {
            source[i] = (float) ((random.nextDouble() - 0.5) * 4.0);
        }
        scratch = new float[size];
        scalarBackend = ScalarDensityMapKernels.INSTANCE;
        vectorBackend = SimdDensityMapKernels.INSTANCE;
    }

    @Benchmark
    public float[] scalarOptimized() {
        System.arraycopy(source, 0, scratch, 0, size);
        scalarBackend.apply(scratch, size, op);
        return scratch;
    }

    @Benchmark
    public float[] vector() {
        System.arraycopy(source, 0, scratch, 0, size);
        vectorBackend.apply(scratch, size, op);
        return scratch;
    }
}
