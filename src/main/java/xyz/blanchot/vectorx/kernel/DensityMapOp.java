package xyz.blanchot.vectorx.kernel;

/**
 * Mirrors the closed set of element-wise transforms real Minecraft 26.2
 * applies via {@code net.minecraft.world.level.levelgen.DensityFunctions.Mapped.Type}
 * (see {@code DensityFunction#abs()}, {@code #square()}, {@code #cube()},
 * {@code #halfNegative()}, {@code #quarterNegative()}, {@code #invert()},
 * {@code #squeeze()} -- these are the only public entry points that create a
 * {@code Mapped} instance). This enum is an independent, standalone
 * reproduction -- {@code Mapped.Type} itself is package-private and not
 * referenced here.
 */
public enum DensityMapOp {
    ABS,
    SQUARE,
    CUBE,
    HALF_NEGATIVE,
    QUARTER_NEGATIVE,
    INVERT,
    SQUEEZE
}
