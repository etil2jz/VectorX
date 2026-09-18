package xyz.blanchot.vectorx;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.blanchot.vectorx.compat.CompatibilityRegistry;
import xyz.blanchot.vectorx.config.VectorXFzzyConfig;
import xyz.blanchot.vectorx.diag.Diagnostics;
import xyz.blanchot.vectorx.diag.VectorXLog;
import xyz.blanchot.vectorx.dispatch.CarverSkipDispatcher;
import xyz.blanchot.vectorx.dispatch.ClampDispatcher;
import xyz.blanchot.vectorx.dispatch.DensityMapDispatcher;
import xyz.blanchot.vectorx.dispatch.KernelDispatcher;
import xyz.blanchot.vectorx.dispatch.MinMaxDispatcher;
import xyz.blanchot.vectorx.dispatch.SelectDispatcher;
import xyz.blanchot.vectorx.dispatch.PackedBitsDispatcher;
import xyz.blanchot.vectorx.kernel.CarverSkipKernels;
import xyz.blanchot.vectorx.kernel.ClampKernels;
import xyz.blanchot.vectorx.kernel.DensityBinaryKernels;
import xyz.blanchot.vectorx.kernel.DensityMapKernels;
import xyz.blanchot.vectorx.kernel.DensitySelectKernels;
import xyz.blanchot.vectorx.kernel.PackedBitsKernels;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod entrypoint. Registers/loads the Fzzy Config-backed config, builds each
 * kernel's fail-open dispatcher, and logs a one-line summary always, plus a
 * full diagnostics block when configured to.
 *
 * <p>This class is deliberately the ONLY place that touches
 * {@link VectorXFzzyConfig}: every other class takes a {@link VectorXConfig}
 * snapshot explicitly, so they stay testable without a running Fabric or
 * Fzzy Config environment.
 */
public class VectorX implements ModInitializer {
    public static final String MOD_ID = "vectorx";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile DensityMapKernels activeDensityMapKernels;
    private static volatile ClampKernels activeClampKernels;
    private static volatile PackedBitsKernels activePackedBitsKernels;
    private static volatile CarverSkipKernels activeCarverSkipKernels;
    private static volatile DensityBinaryKernels activeMinMaxKernels;
    private static volatile DensitySelectKernels activeSelectKernels;

    /**
     * The resolved {@code densityFunctionMap} backend, consulted by
     * {@code mixin.DensityFunctionsMappedMixin}. Available once
     * {@link #onInitialize()} has run.
     */
    public static DensityMapKernels densityMap() {
        DensityMapKernels k = activeDensityMapKernels;
        if (k == null) {
            throw new IllegalStateException("VectorX.densityMap() called before onInitialize()");
        }
        return k;
    }

    /**
     * The resolved {@code densityFunctionClamp} backend, consulted by
     * {@code mixin.DensityFunctionsClampMixin}. Available once
     * {@link #onInitialize()} has run.
     */
    public static ClampKernels clamp() {
        ClampKernels k = activeClampKernels;
        if (k == null) {
            throw new IllegalStateException("VectorX.clamp() called before onInitialize()");
        }
        return k;
    }

    /**
     * The resolved {@code packedStorageUnpack} backend, consulted by
     * {@code mixin.SimpleBitStorageMixin} and {@code mixin.PalettedContainerMixin}.
     * Available once {@link #onInitialize()} has run.
     */
    public static PackedBitsKernels packedBits() {
        PackedBitsKernels k = activePackedBitsKernels;
        if (k == null) {
            throw new IllegalStateException("VectorX.packedBits() called before onInitialize()");
        }
        return k;
    }

    /**
     * The resolved {@code canyonCarverSkip} backend, consulted by
     * {@code mixin.CanyonWorldCarverMixin}. Available once
     * {@link #onInitialize()} has run.
     */
    public static CarverSkipKernels carverSkip() {
        CarverSkipKernels k = activeCarverSkipKernels;
        if (k == null) {
            throw new IllegalStateException("VectorX.carverSkip() called before onInitialize()");
        }
        return k;
    }

    /**
     * The resolved {@code densityFunctionMinMax} backend, consulted by
     * {@code mixin.BinaryFunctionSamplerMixin}. Available once
     * {@link #onInitialize()} has run.
     */
    public static DensityBinaryKernels minMax() {
        DensityBinaryKernels k = activeMinMaxKernels;
        if (k == null) {
            throw new IllegalStateException("VectorX.minMax() called before onInitialize()");
        }
        return k;
    }

    /**
     * The resolved {@code densityFunctionSelect} backend, consulted by
     * {@code mixin.LerpFunctionSamplerMixin} and
     * {@code mixin.RangeChoiceFunctionSamplerMixin}. Available once
     * {@link #onInitialize()} has run.
     */
    public static DensitySelectKernels select() {
        DensitySelectKernels k = activeSelectKernels;
        if (k == null) {
            throw new IllegalStateException("VectorX.select() called before onInitialize()");
        }
        return k;
    }

    @Override
    public void onInitialize() {
        VectorXLog log = new Slf4jLog(LOGGER);

        VectorXFzzyConfig fzzyConfig = ConfigApiJava.registerAndLoadConfig(VectorXFzzyConfig::new, RegisterType.CLIENT);
        VectorXConfig config = fzzyConfig.toSnapshot();

        DensityMapDispatcher densityMapDispatcher = new DensityMapDispatcher(config, log);
        activeDensityMapKernels = densityMapDispatcher.backend();

        ClampDispatcher clampDispatcher = new ClampDispatcher(config, log);
        activeClampKernels = clampDispatcher.backend();

        PackedBitsDispatcher packedBitsDispatcher = new PackedBitsDispatcher(config, log);
        activePackedBitsKernels = packedBitsDispatcher.backend();

        CarverSkipDispatcher carverSkipDispatcher = new CarverSkipDispatcher(config, log);
        activeCarverSkipKernels = carverSkipDispatcher.backend();

        MinMaxDispatcher minMaxDispatcher = new MinMaxDispatcher(config, log);
        activeMinMaxKernels = minMaxDispatcher.backend();

        SelectDispatcher selectDispatcher = new SelectDispatcher(config, log);
        activeSelectKernels = selectDispatcher.backend();

        List<KernelDispatcher> dispatchers = List.of(densityMapDispatcher, clampDispatcher,
                minMaxDispatcher, selectDispatcher, packedBitsDispatcher, carverSkipDispatcher);

        LOGGER.info(Diagnostics.oneLineSummary(dispatchers));

        if (config.diagnosticsEnabled()) {
            List<String> loadedModIds = new ArrayList<>();
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                loadedModIds.add(mod.getMetadata().getId());
            }
            LOGGER.info(Diagnostics.fullReport(config, dispatchers, VectorX.class.getClassLoader(),
                    new CompatibilityRegistry(), loadedModIds));
        }
    }
}
