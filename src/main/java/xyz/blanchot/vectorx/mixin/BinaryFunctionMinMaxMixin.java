package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;
import xyz.blanchot.vectorx.kernel.DensityBinaryOp;

/**
 * Vectorizes the four {@code min}/{@code max} samplers nested in real
 * Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction}.
 *
 * <p><b>Why only min and max, when {@code BinaryFunction} has six ops.</b>
 * {@code add}, {@code sub}, {@code mul} and {@code div} are straight-line
 * arithmetic over two arrays, which C2 already auto-vectorizes on its own:
 * benchmarked at whole-chunk buffer sizes the explicit vector path came out at
 * 0.74x-0.89x of the scalar loop, i.e. a measured loss. {@code min} and
 * {@code max} are the opposite, because vanilla writes them as a
 * <em>conditional store</em> ({@code if (candidate < current) current =
 * candidate}) whose branch depends on the data, which C2 cannot vectorize:
 * the same measurement puts the vector path at 41x-97x net of the benchmark's
 * copy floor. See {@code bench.DensityBinaryBenchmark} and
 * {@code dispatch.MinMaxDispatcher}.
 *
 * <p>That conditional store is also why
 * {@code kernel.simd.SimdDensityBinaryKernels} must not reach for
 * {@code VectorOperators.MIN}: it is specified in terms of
 * {@code Math.min}, which disagrees with vanilla on signed zeros.
 *
 * <p>All four targets are {@code public record}s with public component
 * accessors, so one Mixin class serves them through an {@code instanceof}
 * pattern rather than {@code @Shadow} -- a shadow of {@code rightMinValue}
 * would resolve on {@code MinSampler} and fail on the other three.
 *
 * <p>See {@link ClampFunctionSamplerMixin} for the rationale behind the
 * {@code HEAD} + {@code cancellable} injection and the fail-open contract;
 * both apply here unchanged. The two-buffer targets additionally borrow a
 * scratch buffer from the same arena vanilla would, and release it on every
 * path via try-with-resources.
 */
@Mixin(targets = {
        "net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction$MinSampler",
        "net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction$MaxSampler",
        "net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction$ConstMinSampler",
        "net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction$ConstMaxSampler"
})
public abstract class BinaryFunctionMinMaxMixin {

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            Object self = this;
            if (self instanceof BinaryFunction.MinSampler min) {
                vectorx$twoBuffer(context, outputBuffer, volume, min.left(), min.right(), DensityBinaryOp.MIN);
            } else if (self instanceof BinaryFunction.MaxSampler max) {
                vectorx$twoBuffer(context, outputBuffer, volume, max.left(), max.right(), DensityBinaryOp.MAX);
            } else if (self instanceof BinaryFunction.ConstMinSampler min) {
                min.left().sampleVolume(context, outputBuffer, volume);
                VectorX.minMax().applyConst(vectorx$values(outputBuffer), outputBuffer.size(),
                        DensityBinaryOp.MIN, min.right());
            } else if (self instanceof BinaryFunction.ConstMaxSampler max) {
                max.left().sampleVolume(context, outputBuffer, volume);
                VectorX.minMax().applyConst(vectorx$values(outputBuffer), outputBuffer.size(),
                        DensityBinaryOp.MAX, max.right());
            } else {
                // Unreachable for the declared targets; treated as "not vectorized".
                return;
            }
            ci.cancel();
        } catch (Throwable t) {
            // Do not cancel: the real Mojang method below runs untouched.
            // Never let a dispatch failure propagate into world generation.
        }
    }

    @Unique
    private static void vectorx$twoBuffer(
            SamplerContext context,
            DensityBuffer outputBuffer,
            DensityVolume volume,
            net.minecraft.world.level.levelgen.densityfunction.DensitySampler left,
            net.minecraft.world.level.levelgen.densityfunction.DensitySampler right,
            DensityBinaryOp op
    ) {
        left.sampleVolume(context, outputBuffer, volume);
        // Same arena buffer vanilla borrows, released on every path.
        try (ScopedDensityBuffer rightBuffer = context.acquireBuffer(volume)) {
            right.sampleVolume(context, rightBuffer, volume);
            // size(), not values.length: the arena slot's capacity runs past the
            // live sample, and those slots belong to the pool.
            VectorX.minMax().applyBuffer(vectorx$values(outputBuffer), vectorx$values(rightBuffer),
                    outputBuffer.size(), op);
        }
    }

    @Unique
    private static float[] vectorx$values(DensityBuffer buffer) {
        return ((DensityBufferAccessor) buffer).vectorx$values();
    }
}
