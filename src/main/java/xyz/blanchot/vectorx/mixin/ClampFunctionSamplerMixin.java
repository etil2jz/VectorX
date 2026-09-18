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
 * Vectorizes the element-wise clamp in real Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction$Sampler}.
 *
 * <p>26.3 replaced 26.2's {@code DensityFunctions$Clamp} /
 * {@code PureTransformer.fillArray(double[], ContextProvider)} pair with a
 * compiled-sampler pipeline: {@code ClampFunction} (the declarative node)
 * compiles to a nested {@code Sampler} record whose
 * {@code sampleVolume(SamplerContext, DensityBuffer, DensityVolume)} first
 * delegates to its input sampler and then runs
 * {@code for (i &lt; outputBuffer.size()) outputBuffer.set(i, Mth.clamp(outputBuffer.get(i), min, max))}.
 * That loop is what this replaces.
 *
 * <p><b>Why {@code @Inject} at {@code HEAD} and not a narrower injection
 * point.</b> {@code sampleVolume} is a concrete method on a record, so the
 * 26.2 trick of adding a missing override is gone. Injecting at {@code HEAD}
 * with {@code cancellable = true} lets this method do the whole job -- the
 * delegation plus the vectorized clamp -- and then cancel, and it depends on
 * nothing but the method's own signature, so it cannot be broken by a change
 * to the loop's shape or to which helper Mojang calls. The price is that on
 * the failure path the input sampler is asked for its volume twice (once
 * here, once by the real method that then runs): density samplers are pure,
 * so the result is unchanged and only work is wasted, and that path is only
 * ever reached if the vector dispatch itself throws.
 *
 * <p><b>Fail-open.</b> Anything at all going wrong -- the
 * {@link DensityBufferAccessor} cast failing because the accessor Mixin did
 * not apply, {@code VectorX.clamp()} being called before initialization, a
 * {@code LinkageError} from the incubator Vector API, an out-of-range length
 * -- is caught and simply not cancelled, so the real, unmodified Mojang
 * method runs and world generation is bit-identical to vanilla.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction$Sampler")
public abstract class ClampFunctionSamplerMixin {

    @Shadow
    @Final
    private DensitySampler input;

    @Shadow
    @Final
    private float min;

    @Shadow
    @Final
    private float max;

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            this.input.sampleVolume(context, outputBuffer, volume);
            float[] values = ((DensityBufferAccessor) outputBuffer).vectorx$values();
            // size(), not values.length: a pooled ScopedDensityBuffer's array is
            // the arena slot's capacity and the slots past size() belong to the pool.
            VectorX.clamp().clampInPlace(values, outputBuffer.size(), this.min, this.max);
            ci.cancel();
        } catch (Throwable t) {
            // Do not cancel: the real Mojang method below runs untouched.
            // Never let a dispatch failure propagate into world generation.
        }
    }
}
