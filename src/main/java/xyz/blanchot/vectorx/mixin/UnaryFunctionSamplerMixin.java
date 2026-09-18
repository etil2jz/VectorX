package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.blanchot.vectorx.VectorX;
import xyz.blanchot.vectorx.kernel.DensityMapOp;

/**
 * Vectorizes the element-wise transform of the two
 * {@code net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction}
 * samplers that measurably benefit from it in real Minecraft 26.3:
 * {@code UnaryFunction$SqueezeSampler} and {@code UnaryFunction$LeakyReLUSampler}.
 *
 * <p><b>What 26.3 changed.</b> 26.2 had a single {@code DensityFunctions$Mapped}
 * class carrying a {@code Mapped.Type} enum and one
 * {@code transform(Type, double)} switch, which is why the 26.2 Mixin had to
 * resolve {@code Mapped.type()} reflectively (the enum was nested in a
 * {@code protected} class and unnameable from this package) and then decide
 * per call whether the op was one worth vectorizing. 26.3 compiles each
 * {@code UnaryFunction.Type} down to its own dedicated sampler record, so the
 * op is now encoded in the class identity: targeting exactly the two sampler
 * classes below <em>is</em> the op filter, and all of that reflection is gone.
 * Every other op's sampler is simply never touched and runs as vanilla.
 *
 * <p>{@code LeakyReLUSampler} covers both {@code HALF_NEGATIVE}
 * ({@code negativeFactor = 0.5F}) and {@code QUARTER_NEGATIVE}
 * ({@code 0.25F}) -- those are the only two values
 * {@code UnaryFunction.compileSampler} ever constructs, verified in the 26.3
 * source. The record is public though, so its canonical constructor accepts
 * any factor; this forwards whatever {@code negativeFactor()} reports to a
 * kernel that is correct for an arbitrary factor rather than assuming one of
 * the two.
 *
 * <p><b>Why one class with two targets.</b> The record components are
 * {@code private final} fields, so {@code @Shadow @Final} would be the usual
 * way in -- but {@code negativeFactor} exists on only one of the two targets,
 * and a shadow that resolves on one target and not the other fails Mixin
 * application. Records generate <em>public</em> component accessors, so
 * {@code input()} and {@code negativeFactor()} are reached through a plain
 * cast of {@code this} instead; the {@code instanceof} pattern below is what
 * distinguishes the two targets a single merged method body now serves. (The
 * alternative, one Mixin class per target, would have needed a second entry
 * in {@code vectorx.mixins.json}.)
 *
 * <p>See {@link ClampFunctionSamplerMixin} for the rationale behind the
 * {@code HEAD} + {@code cancellable} injection and the fail-open contract;
 * both apply here unchanged.
 */
@Mixin(targets = {
        "net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction$SqueezeSampler",
        "net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction$LeakyReLUSampler"
})
public abstract class UnaryFunctionSamplerMixin {

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            Object self = this;
            if (self instanceof UnaryFunction.SqueezeSampler squeeze) {
                squeeze.input().sampleVolume(context, outputBuffer, volume);
                // size(), not values.length: a pooled ScopedDensityBuffer's array is
                // the arena slot's capacity and the slots past size() belong to the pool.
                VectorX.densityMap().apply(vectorx$values(outputBuffer), outputBuffer.size(), DensityMapOp.SQUEEZE);
            } else if (self instanceof UnaryFunction.LeakyReLUSampler leakyReLU) {
                leakyReLU.input().sampleVolume(context, outputBuffer, volume);
                VectorX.densityMap()
                        .leakyReLU(vectorx$values(outputBuffer), outputBuffer.size(), leakyReLU.negativeFactor());
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
    private static float[] vectorx$values(DensityBuffer buffer) {
        return ((DensityBufferAccessor) buffer).vectorx$values();
    }
}
