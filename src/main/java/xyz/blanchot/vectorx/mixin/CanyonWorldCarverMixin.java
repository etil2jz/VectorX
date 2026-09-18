package xyz.blanchot.vectorx.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.blanchot.vectorx.VectorX;
import xyz.blanchot.vectorx.carve.CanyonCarveGeometry;

/**
 * Vectorizes {@code CanyonWorldCarver}'s ellipsoid skip test -- the inner
 * {@code worldY} loop of {@code WorldCarver.carveEllipsoid} -- for the one
 * carver where it's both cleanly reachable and worth it: see
 * {@code bench.CarverShouldSkipBenchmark} (an exploratory JMH prototype, not
 * the shipped kernel itself, but using the same true-division formula as
 * {@code SimdCarverSkipKernels} -- an earlier measurement against a
 * precomputed-reciprocal stand-in overstated the gain) for the measured
 * range: negligible below one vector's lane width (~8 for AVX-512 double --
 * too short a Y-sweep never fills a lane), up to roughly 3.4x once it does.
 * Canyon's typical range (yScale=3.0 in vanilla's own {@code canyon.json})
 * lands well past that width. Correctness of
 * the shipped path -- both the skip-mask math and the surrounding bounds/
 * traversal reimplementation this Mixin owns -- is covered by
 * {@code CarverSkipDifferentialTest} and {@code CanyonCarveGeometryTest}.
 *
 * <p>{@code CaveWorldCarver} is deliberately NOT covered: its skip test
 * captures {@code floorLevel}, a value sampled two call frames up in
 * {@code carve()}, not visible at any point this Mixin could reach without
 * either reflecting into the {@code CarveSkipChecker} lambda's captured
 * fields (fragile, unsupported by the JLS) or reimplementing the RNG-heavy
 * {@code createTunnel} loop verbatim (real risk: a single misplaced
 * {@code random.nextFloat()} call would silently desync generated terrain
 * for a given seed from vanilla -- a correctness bug the try/catch
 * fail-open below cannot catch, since it wouldn't throw). Canyon's
 * {@code widthFactorPerHeight} has no such problem: it's a local declared
 * in {@code doCarve} itself, in the same scope as the {@code carveEllipsoid}
 * call this redirects, captured via MixinExtras {@code @Local} with zero
 * effect on RNG draw order.
 *
 * <p>{@code carveEllipsoid} itself is neither touched nor reimplemented in
 * the sense of replacing its bytecode -- this only redirects the ONE call
 * site inside {@code doCarve}, so the real, unmodified method stays
 * perfectly intact and is exactly what the fallback below calls on any
 * failure. Since 26.3 made it a {@code public static void} method on the
 * {@code WorldCarver} interface, that fallback is a plain static call and
 * the accessor mixin the 26.2 version needed (to bridge the then-{@code
 * protected} {@code carveEllipsoid} and {@code carveBlock}) is gone.
 *
 * <p>What {@link CanyonCarveGeometry} runs instead is a faithful
 * reimplementation of that same method body (bounds computation, X/Z/Y
 * triple loop, {@code CarverOutput.carve} dispatch -- all pure geometry, no
 * RNG), with only the Y-loop's skip decision computed in bulk via
 * {@link VectorX#carverSkip()} instead of Mojang's per-element
 * {@code skipChecker.shouldSkip} call. The vectorized inner loop runs the
 * skip test ascending; the dispatch loop right after it still walks
 * {@code worldY} descending, exactly like vanilla. In 26.3 that order is no
 * longer load-bearing for vanilla itself -- {@code carveBlock} and its
 * {@code hasGrass} top-to-bottom column bookkeeping were removed, and the
 * carver now only sets bits in a {@code CarvingMask}, which
 * {@code NoiseBasedChunkGenerator.applyCarvingMask} later replays in
 * {@code BitSet} index order -- but {@code CarverOutput} is a public
 * interface anyone may implement, so mirroring vanilla's exact call
 * sequence is kept as a free guarantee.
 */
@Mixin(CanyonWorldCarver.class)
public abstract class CanyonWorldCarverMixin {

    @Unique
    private static void vectorx$vectorizedCarveEllipsoid(
            WorldGenerationContext context,
            ChunkPos chunkPos,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            CarverOutput output,
            float[] widthFactorPerHeight
    ) {
        CanyonCarveGeometry.sweepVectorized(chunkPos, x, y, z, horizontalRadius, verticalRadius,
                output.minY(), output.maxY(), context.getMinGenY(),
                widthFactorPerHeight, VectorX.carverSkip(), output::carve);
    }

    @Redirect(
            method = "doCarve",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/carver/WorldCarver;carveEllipsoid("
                            + "Lnet/minecraft/world/level/ChunkPos;"
                            + "DDDDD"
                            + "Lnet/minecraft/world/level/chunk/CarverOutput;"
                            + "Lnet/minecraft/world/level/levelgen/carver/WorldCarver$CarveSkipChecker;"
                            + ")V"
            )
    )
    private void vectorx$carveEllipsoid(
            ChunkPos chunkPos,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            CarverOutput output,
            WorldCarver.CarveSkipChecker skipChecker,
            @Local(argsOnly = true) WorldGenerationContext context,
            @Local float[] widthFactorPerHeight
    ) {
        try {
            vectorx$vectorizedCarveEllipsoid(context, chunkPos, x, y, z,
                    horizontalRadius, verticalRadius, output, widthFactorPerHeight);
        } catch (Throwable t) {
            // Fall through to the exact real carveEllipsoid, unmodified;
            // never let a dispatch or reimplementation failure propagate
            // into world generation. Note this may re-carve positions the
            // failed attempt already reported -- harmless, since 26.3's
            // CarverOutput.carve is an idempotent BitSet set in vanilla.
            WorldCarver.carveEllipsoid(chunkPos, x, y, z, horizontalRadius, verticalRadius,
                    output, skipChecker);
        }
    }
}
