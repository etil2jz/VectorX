package xyz.blanchot.vectorx.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.blanchot.vectorx.VectorX;
import xyz.blanchot.vectorx.carve.CanyonCarveGeometry;

import java.util.function.Function;

/**
 * Vectorizes {@code CanyonWorldCarver}'s ellipsoid skip test -- the inner
 * {@code worldY} loop of {@code WorldCarver.carveEllipsoid} -- for the one
 * carver where it's both cleanly reachable and worth it: see
 * {@code bench.CarverShouldSkipBenchmark} (an exploratory JMH prototype, not
 * the shipped kernel itself) for the measured 1.4x-3.9x range across
 * realistic vertical-radius sizes, canyon's typical range (yScale=3.0 in
 * vanilla's own {@code canyon.json}) landing at the high end. Correctness of
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
 * failure. What follows is a faithful reimplementation of that same method
 * body (bounds computation, X/Z/Y triple loop, mask/carveBlock dispatch --
 * all pure geometry, no RNG), with only the Y-loop's skip decision computed
 * in bulk via {@link VectorX#carverSkip()} instead of Mojang's per-element
 * {@code skipChecker.shouldSkip} call. The vectorized inner loop runs the
 * skip test ascending; the carve-dispatch loop right after it still walks
 * {@code worldY} descending, exactly like vanilla, since {@code carveBlock}'s
 * grass-preservation logic depends on that top-to-bottom order within a
 * column.
 */
@Mixin(CanyonWorldCarver.class)
public abstract class CanyonWorldCarverMixin {

    @Redirect(
            method = "doCarve",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/carver/WorldCarver;carveEllipsoid("
                            + "Lnet/minecraft/world/level/levelgen/carver/CarvingContext;"
                            + "Lnet/minecraft/world/level/levelgen/carver/CarverConfiguration;"
                            + "Lnet/minecraft/world/level/chunk/ChunkAccess;"
                            + "Ljava/util/function/Function;"
                            + "Lnet/minecraft/world/level/levelgen/Aquifer;"
                            + "DDDDD"
                            + "Lnet/minecraft/world/level/chunk/CarvingMask;"
                            + "Lnet/minecraft/world/level/levelgen/carver/WorldCarver$CarveSkipChecker;"
                            + ")Z"
            )
    )
    private boolean vectorx$carveEllipsoid(
            CanyonWorldCarver instance,
            CarvingContext context,
            CarverConfiguration configuration,
            ChunkAccess chunk,
            Function<BlockPos, Holder<Biome>> biomeGetter,
            Aquifer aquifer,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            CarvingMask mask,
            WorldCarver.CarveSkipChecker skipChecker,
            @Local float[] widthFactorPerHeight
    ) {
        try {
            return vectorx$vectorizedCarveEllipsoid(instance, context, configuration, chunk, biomeGetter, aquifer,
                    x, y, z, horizontalRadius, verticalRadius, mask, widthFactorPerHeight);
        } catch (Throwable t) {
            // Fall through to the exact real carveEllipsoid, unmodified;
            // never let a dispatch or reimplementation failure propagate
            // into world generation.
            return ((WorldCarverAccessor) instance).vectorx$invokeCarveEllipsoid(context, configuration, chunk,
                    biomeGetter, aquifer, x, y, z, horizontalRadius, verticalRadius, mask, skipChecker);
        }
    }

    @Unique
    private static boolean vectorx$vectorizedCarveEllipsoid(
            CanyonWorldCarver instance,
            CarvingContext context,
            CarverConfiguration configuration,
            ChunkAccess chunk,
            Function<BlockPos, Holder<Biome>> biomeGetter,
            Aquifer aquifer,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            CarvingMask mask,
            float[] widthFactorPerHeight
    ) {
        ChunkPos chunkPos = chunk.getPos();
        boolean debugEnabled = SharedConstants.DEBUG_CARVERS || configuration.debugSettings.isDebugMode();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos helperPos = new BlockPos.MutableBlockPos();
        MutableBoolean hasGrass = new MutableBoolean(false);
        // sink calls are strictly ordered by (xIndex, zIndex, descending worldY), same as
        // vanilla's loop nesting, so a column change is detectable from consecutive calls;
        // vanilla resets hasGrass at the top of every z-loop iteration, before its worldY loop.
        int[] lastColumn = {Integer.MIN_VALUE, Integer.MIN_VALUE};

        return CanyonCarveGeometry.sweepVectorized(chunkPos, x, y, z, horizontalRadius, verticalRadius,
                context.getMinGenY(), context.getGenDepth(), chunk.isUpgrading(), debugEnabled,
                widthFactorPerHeight, VectorX.carverSkip(), mask,
                (xIndex, worldY, zIndex) -> {
                    if (lastColumn[0] != xIndex || lastColumn[1] != zIndex) {
                        hasGrass.setFalse();
                        lastColumn[0] = xIndex;
                        lastColumn[1] = zIndex;
                    }
                    blockPos.set(chunkPos.getBlockX(xIndex), worldY, chunkPos.getBlockZ(zIndex));
                    ((WorldCarverAccessor) instance).vectorx$invokeCarveBlock(context, configuration,
                            chunk, biomeGetter, mask, blockPos, helperPos, aquifer, hasGrass);
                });
    }
}
