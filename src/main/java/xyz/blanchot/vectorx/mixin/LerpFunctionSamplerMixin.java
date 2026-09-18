package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;

/**
 * Vectorizes real Minecraft 26.3's
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction$Sampler}.
 *
 * <p>Vanilla's loop is a three-way per-element branch -- {@code alpha == 0} ->
 * {@code first}, {@code alpha == 1} -> {@code second}, otherwise
 * {@code Mth.lerp} -- which C2 cannot auto-vectorize. Replacing it with two
 * lane masks and a pair of blends measured 6.9x-8.4x net of the benchmark's
 * copy floor (see {@code bench.DensitySelectBenchmark}).
 *
 * <p>The two constant branches are reproduced rather than folded away because
 * they are <em>semantic</em>: {@code first + 1.0F * (second - first)} is not
 * {@code second} for every pair of floats, so dropping the special case would
 * change generated terrain.
 *
 * <p><b>Not covered:</b> {@code LerpFunction$ConstFirstSampler} and
 * {@code $ConstSecondSampler}, the shapes the compiler emits when one endpoint
 * folds to a literal. They would each need their own broadcast-constant kernel
 * entry point, and they are left running vanilla rather than grown speculatively
 * -- an unhooked sampler is simply never touched.
 *
 * <p>See {@link ClampFunctionSamplerMixin} for the rationale behind the
 * {@code HEAD} + {@code cancellable} injection and the fail-open contract.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction$Sampler")
public abstract class LerpFunctionSamplerMixin {

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            LerpFunction.Sampler self = (LerpFunction.Sampler) (Object) this;
            self.alpha().sampleVolume(context, outputBuffer, volume);
            // Same two arena buffers vanilla borrows, acquired in the same
            // nested order (a child sampler may borrow from the arena itself,
            // so the order decides which slots everyone gets) and released on
            // every path.
            try (ScopedDensityBuffer firstBuffer = context.acquireBuffer(volume)) {
                self.first().sampleVolume(context, firstBuffer, volume);
                try (ScopedDensityBuffer secondBuffer = context.acquireBuffer(volume)) {
                    self.second().sampleVolume(context, secondBuffer, volume);
                    // size(), not values.length: the arena slot's capacity runs
                    // past the live sample, and those slots belong to the pool.
                    VectorX.select().lerp(vectorx$values(outputBuffer), vectorx$values(firstBuffer),
                            vectorx$values(secondBuffer), outputBuffer.size());
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
