package xyz.blanchot.vectorx.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import xyz.blanchot.vectorx.VectorX;

/**
 * Vectorizes {@code DensityFunctions$Clamp}'s element-wise transform.
 *
 * <p>Like {@code Mapped}, {@code Clamp} inherits {@code fillArray} as a
 * default method from {@code PureTransformer} with no override of its own,
 * so this Mixin adds one -- simpler than {@code DensityFunctionsMappedMixin}'s,
 * since {@code minValue}/{@code maxValue} are plain {@code double} fields
 * shadowed directly, no reflection needed.
 *
 * <p>On any failure from the vector dispatch, this falls back to
 * {@code this.transform(double)} per element -- the same real Mojang method
 * the inherited default would have called.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Clamp")
public abstract class DensityFunctionsClampMixin {

    @Shadow
    @Final
    private DensityFunction input;

    @Shadow
    @Final
    private double minValue;

    @Shadow
    @Final
    private double maxValue;

    @Shadow
    public abstract double transform(double input);

    @Unique
    @Dynamic("Overrides DensityFunctions$PureTransformer's default fillArray, which Clamp inherits without its own override")
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        this.input.fillArray(output, contextProvider);

        try {
            VectorX.clamp().clampInPlace(output, this.minValue, this.maxValue);
            return;
        } catch (Throwable t) {
            // Fall through to the always-correct per-element path below;
            // never let a dispatch failure propagate into world generation.
        }

        for (int i = 0; i < output.length; i++) {
            output[i] = this.transform(output[i]);
        }
    }
}
