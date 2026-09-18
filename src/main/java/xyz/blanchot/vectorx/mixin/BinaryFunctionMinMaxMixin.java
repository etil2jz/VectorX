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

@Mixin(targets = {
        "net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction$MinSampler",
        "net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction$MaxSampler",
        "net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction$ConstMinSampler",
        "net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction$ConstMaxSampler"
})
public abstract class BinaryFunctionMinMaxMixin {

    @Unique
    private static void vectorx$twoBuffer(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, net.minecraft.world.level.levelgen.densityfunction.DensitySampler left, net.minecraft.world.level.levelgen.densityfunction.DensitySampler right, DensityBinaryOp op) {
        left.sampleVolume(context, outputBuffer, volume);
        try (ScopedDensityBuffer rightBuffer = context.acquireBuffer(volume)) {
            right.sampleVolume(context, rightBuffer, volume);
            VectorX.minMax().applyBuffer(vectorx$values(outputBuffer), vectorx$values(rightBuffer), outputBuffer.size(), op);
        }
    }

    @Unique
    private static float[] vectorx$values(DensityBuffer buffer) {
        return ((DensityBufferAccessor) buffer).vectorx$values();
    }

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
                VectorX.minMax().applyConst(vectorx$values(outputBuffer), outputBuffer.size(), DensityBinaryOp.MIN, min.right());
            } else if (self instanceof BinaryFunction.ConstMaxSampler max) {
                max.left().sampleVolume(context, outputBuffer, volume);
                VectorX.minMax().applyConst(vectorx$values(outputBuffer), outputBuffer.size(), DensityBinaryOp.MAX, max.right());
            } else {
                return;
            }
            ci.cancel();
        } catch (Throwable _) {
        }
    }
}
