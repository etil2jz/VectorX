package xyz.blanchot.vectorx.kernel.scalar;

import xyz.blanchot.vectorx.kernel.DensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.DensityBinaryOp;
import xyz.blanchot.vectorx.kernel.SelfDescribing;

import java.util.Objects;

/**
 * Reference scalar implementation of {@link DensityBinaryKernels}. Each loop
 * body below was copied from the corresponding sampler record nested in real
 * Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction},
 * not re-derived from memory:
 *
 * <ul>
 *   <li>{@code AddSampler}: {@code outputBuffer.addTo(i, rightBuffer.get(i))}</li>
 *   <li>{@code SubSampler}: {@code outputBuffer.addTo(i, -rightBuffer.get(i))}
 *       -- spelled as an add of the negation, which IEEE-754 defines to be
 *       exactly {@code a - b}, so it is written as a subtraction here</li>
 *   <li>{@code MulSampler}: {@code get(i) * rightBuffer.get(i)}</li>
 *   <li>{@code DivSampler}: {@code get(i) / rightBuffer.get(i)}</li>
 *   <li>{@code MinSampler}: {@code if (r < get(i)) set(i, r)}</li>
 *   <li>{@code MaxSampler}: {@code if (r > get(i)) set(i, r)}</li>
 *   <li>{@code ConstAddSampler}: {@code addTo(i, right)}</li>
 *   <li>{@code ConstSubSampler}: {@code set(i, left - get(i))}</li>
 *   <li>{@code ConstMulSampler}: {@code set(i, get(i) * right)}</li>
 *   <li>{@code ConstDivSampler}: {@code set(i, left / get(i))}</li>
 *   <li>{@code ConstMinSampler}: {@code if (right < get(i)) set(i, right)}</li>
 *   <li>{@code ConstMaxSampler}: {@code if (right > get(i)) set(i, right)}</li>
 * </ul>
 *
 * <p>The {@code MIN}/{@code MAX} conditional writes are deliberately kept as
 * comparisons rather than {@code Math.min}/{@code Math.max}: the two differ on
 * signed zeros. See {@link DensityBinaryKernels}.
 */
public final class ScalarDensityBinaryKernels implements DensityBinaryKernels, SelfDescribing {

    public static final ScalarDensityBinaryKernels INSTANCE = new ScalarDensityBinaryKernels();

    private ScalarDensityBinaryKernels() {
    }

    /**
     * Mirrors the two-buffer samplers: {@code left} is the running output
     * value, {@code right} the value the right-hand child produced.
     */
    public static float combine(DensityBinaryOp op, float left, float right) {
        return switch (op) {
            case ADD -> left + right;
            case SUB -> left - right;
            case MUL -> left * right;
            case DIV -> left / right;
            case MIN -> right < left ? right : left;
            case MAX -> right > left ? right : left;
        };
    }

    /**
     * Mirrors the {@code Const*} samplers. Note the operand order for
     * {@code SUB} and {@code DIV}: vanilla puts the constant on the left.
     */
    public static float combineConst(DensityBinaryOp op, float value, float operand) {
        return switch (op) {
            case ADD -> value + operand;
            case SUB -> operand - value;
            case MUL -> value * operand;
            case DIV -> operand / value;
            case MIN -> operand < value ? operand : value;
            case MAX -> operand > value ? operand : value;
        };
    }

    @Override
    public void applyBuffer(float[] values, float[] right, int length, DensityBinaryOp op) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(op, "op");
        Objects.checkFromIndexSize(0, length, values.length);
        Objects.checkFromIndexSize(0, length, right.length);
        for (int i = 0; i < length; i++) {
            values[i] = combine(op, values[i], right[i]);
        }
    }

    @Override
    public void applyConst(float[] values, int length, DensityBinaryOp op, float operand) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(op, "op");
        Objects.checkFromIndexSize(0, length, values.length);
        for (int i = 0; i < length; i++) {
            values[i] = combineConst(op, values[i], operand);
        }
    }

    @Override
    public String describe() {
        return "scalar reference backend for density-function element-wise binary ops";
    }
}
