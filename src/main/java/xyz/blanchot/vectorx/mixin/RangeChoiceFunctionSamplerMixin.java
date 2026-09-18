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

    @Unique
    private static float[] vectorx$values(DensityBuffer buffer) {
        return ((DensityBufferAccessor) buffer).vectorx$values();
    }

    @Inject(method = "sampleVolume", at = @At("HEAD"), cancellable = true)
    private void vectorx$sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, CallbackInfo ci) {
        try {
            this.whenInRange.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer inputBuffer = context.acquireBuffer(volume)) {
                this.input.sampleVolume(context, inputBuffer, volume);
                try (ScopedDensityBuffer outOfRangeBuffer = context.acquireBuffer(volume)) {
                    this.whenOutOfRange.sampleVolume(context, outOfRangeBuffer, volume);
                    VectorX.select().rangeChoice(vectorx$values(outputBuffer), vectorx$values(inputBuffer), vectorx$values(outOfRangeBuffer), outputBuffer.size(), this.minInclusive, this.maxExclusive);
                }
            }
            ci.cancel();
        } catch (Throwable _) {
        }
    }
}
