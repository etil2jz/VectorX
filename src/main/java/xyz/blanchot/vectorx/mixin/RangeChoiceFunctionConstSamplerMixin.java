package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;

/**
 * Vectorizes the fully-constant sampler of real Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction}
 * -- the shape the compiler emits when both branches fold to literals, which
 * reduces the whole node to "replace each element with one of two constants
 * depending on which side of a range it falls".
 *
 * <p>That is the cheapest possible arithmetic wrapped around a data-dependent
 * branch, which is exactly the case C2 handles worst and a lane mask handles
 * best: it measured 9.3x-79x net of the benchmark's copy floor, the largest
 * margin of any op in this project (see {@code bench.DensitySelectBenchmark}).
 *
 * <p>This is a separate Mixin class from
 * {@link RangeChoiceFunctionSamplerMixin} rather than a second target on it
 * because the two records share field <em>names</em> but not field
 * <em>types</em> -- {@code whenInRange} and {@code whenOutOfRange} are
 * {@code DensitySampler}s there and {@code float}s here -- so no single set of
 * shadows can resolve against both.
 *
 * <p>See {@link ClampFunctionSamplerMixin} for the rationale behind the
 * {@code HEAD} + {@code cancellable} injection and the fail-open contract.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction$ConstSampler")
public abstract class RangeChoiceFunctionConstSamplerMixin {

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
    private float whenInRange;

    @Shadow
    @Final
    private float whenOutOfRange;

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            this.input.sampleVolume(context, outputBuffer, volume);
            // size(), not values.length: the arena slot's capacity runs past the
            // live sample, and those slots belong to the pool.
            VectorX.select().rangeChoiceConst(
                    ((DensityBufferAccessor) outputBuffer).vectorx$values(), outputBuffer.size(),
                    this.minInclusive, this.maxExclusive, this.whenInRange, this.whenOutOfRange);
            ci.cancel();
        } catch (Throwable t) {
            // Do not cancel: the real Mojang method below runs untouched.
            // Never let a dispatch failure propagate into world generation.
        }
    }
}
