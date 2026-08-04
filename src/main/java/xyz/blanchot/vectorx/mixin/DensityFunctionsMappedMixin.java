package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import xyz.blanchot.vectorx.VectorX;
import xyz.blanchot.vectorx.diag.VectorXLog;
import xyz.blanchot.vectorx.kernel.DensityMapOp;

import java.lang.reflect.Method;

/**
 * Vectorizes {@code DensityFunctions$Mapped}'s element-wise transform, but
 * only for {@code half_negative}, {@code quarter_negative} and
 * {@code squeeze} -- the other ops measured no real gain (branch-free
 * already), so this Mixin leaves them untouched.
 *
 * <p>{@code Mapped} inherits {@code fillArray} as a default method from
 * {@code PureTransformer} with no override of its own, so this Mixin adds
 * one: on any unvectorized op, or anything unexpected, it falls back to
 * {@code this.transform(double)} per element -- the same real Mojang method
 * the inherited default would have called.
 *
 * <p>{@code Mapped.Type} is nested inside a {@code protected} class, so it
 * can't be named from this package at compile time; reflection resolves it
 * once per JVM instead. If that lookup ever fails (a future MC build
 * renames/restructures this), {@link #fastOp} always returns {@code null},
 * routing every call through the always-correct fallback.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Mapped")
public abstract class DensityFunctionsMappedMixin {

    @Unique
    private static final Method TYPE_METHOD = resolveTypeMethod();
    @Shadow
    @Final
    private DensityFunction input;

    @Unique
    private static Method resolveTypeMethod() {
        try {
            Class<?> mappedClass = Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$Mapped");
            return mappedClass.getMethod("type");
        } catch (ReflectiveOperationException e) {
            VectorXLog.console().warn("densityFunctionMap: could not resolve Mapped.type() reflectively ("
                    + e + "); this op will never be vectorized, vanilla behavior is otherwise unaffected");
            return null;
        }
    }

    @Shadow
    public abstract double transform(double input);

    @Unique
    @Dynamic("Overrides DensityFunctions$PureTransformer's default fillArray, which Mapped inherits without its own override")
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        this.input.fillArray(output, contextProvider);

        DensityMapOp op = fastOp();
        if (op != null) {
            try {
                VectorX.densityMap().apply(output, op);
                return;
            } catch (Throwable t) {
                // Fall through to the always-correct per-element path below;
                // never let a dispatch failure propagate into world generation.
            }
        }

        for (int i = 0; i < output.length; i++) {
            output[i] = this.transform(output[i]);
        }
    }

    /**
     * Returns the op to vectorize, or {@code null} to always use the vanilla-equivalent fallback.
     */
    @Unique
    private DensityMapOp fastOp() {
        if (TYPE_METHOD == null) {
            return null;
        }
        try {
            Object type = TYPE_METHOD.invoke(this);
            DensityMapOp op = DensityMapOp.valueOf(((Enum<?>) type).name());
            return switch (op) {
                case HALF_NEGATIVE, QUARTER_NEGATIVE, SQUEEZE -> op;
                default -> null;
            };
        } catch (ReflectiveOperationException | IllegalArgumentException | ClassCastException e) {
            return null;
        }
    }
}
