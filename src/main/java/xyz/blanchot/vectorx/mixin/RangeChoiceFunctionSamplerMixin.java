package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;

/**
 * Vectorizes the general, three-buffer sampler of real Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction}.
 *
 * <p>Vanilla's loop is a per-element conditional store -- overwrite the output
 * from the out-of-range buffer wherever the input falls outside
 * {@code [minInclusive, maxExclusive)} -- which C2 cannot auto-vectorize.
 * Turning the branch into a lane mask and a blend measured 5.4x-42x net of the
 * benchmark's copy floor (see {@code bench.DensitySelectBenchmark}).
 *
 * <p>Vanilla spells the test as the negation of "in range"
 * ({@code !(input >= min) || !(input < max)}), which is what makes a NaN input
 * take the out-of-range side; {@code kernel.simd.SimdDensitySelectKernels}
 * builds the mask the same way rather than inverting the comparison, so NaN
 * behaviour matches by construction.
 *
 * <p>Unlike {@link BinaryFunctionMinMaxMixin}'s targets, this sampler is a
 * package-private record, so its components cannot be reached through a cast
 * from this package and are shadowed instead.
 *
 * <p>See {@link ClampFunctionSamplerMixin} for the rationale behind the
 * {@code HEAD} + {@code cancellable} injection and the fail-open contract.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction$Sampler")
public abstract class RangeChoiceFunctionSamplerMixin {

    @Shadow
    @Final
    private DensitySampler input;

    @Shadow
    @Final
    private float minInclusive;

    @Shadow
    @Final
    private float maxExclusive;

    @Shadow
    @Final
    private DensitySampler whenInRange;

    @Shadow
    @Final
    private DensitySampler whenOutOfRange;

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            this.whenInRange.sampleVolume(context, outputBuffer, volume);
            // Same two arena buffers vanilla borrows, acquired in the same
            // nested order and released on every path.
            try (ScopedDensityBuffer inputBuffer = context.acquireBuffer(volume)) {
                this.input.sampleVolume(context, inputBuffer, volume);
                try (ScopedDensityBuffer outOfRangeBuffer = context.acquireBuffer(volume)) {
                    this.whenOutOfRange.sampleVolume(context, outOfRangeBuffer, volume);
                    // size(), not values.length: the arena slot's capacity runs
                    // past the live sample, and those slots belong to the pool.
                    VectorX.select().rangeChoice(vectorx$values(outputBuffer), vectorx$values(inputBuffer),
                            vectorx$values(outOfRangeBuffer), outputBuffer.size(),
                            this.minInclusive, this.maxExclusive);
                }
            }
            ci.cancel();
        } catch (Throwable t) {
            // Do not cancel: the real Mojang method below runs untouched.
            // Never let a dispatch failure propagate into world generation.
        }
    }

    @Unique
    private static float[] vectorx$values(DensityBuffer buffer) {
        return ((DensityBufferAccessor) buffer).vectorx$values();
    }
}
