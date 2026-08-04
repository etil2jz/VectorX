package xyz.blanchot.vectorx.dispatch;

import org.junit.jupiter.api.Test;
import xyz.blanchot.vectorx.VectorXConfig;
import xyz.blanchot.vectorx.VectorXConfig.KernelMode;
import xyz.blanchot.vectorx.diag.VectorXLog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DensityMapDispatcherTest {

    @Test
    void resolvesToVectorByDefaultOnThisTestJvm() {
        // The test task runs with --add-modules=jdk.incubator.vector, so "auto"
        // should resolve to the vector backend and pass its self-test.
        DensityMapDispatcher dispatcher = new DensityMapDispatcher(VectorXConfig.defaults(), VectorXLog.noop());
        assertTrue(dispatcher.isVector());
        assertNull(dispatcher.disableReason());
    }

    @Test
    void forceScalarSystemPropertyDisablesVector() {
        System.setProperty(VectorModuleProbe.FORCE_SCALAR_PROPERTY, "true");
        try {
            DensityMapDispatcher dispatcher = new DensityMapDispatcher(VectorXConfig.defaults(), VectorXLog.noop());
            assertFalse(dispatcher.isVector());
            assertNotNull(dispatcher.disableReason());
        } finally {
            System.clearProperty(VectorModuleProbe.FORCE_SCALAR_PROPERTY);
        }
    }

    @Test
    void vectorBackendLoadFailureFallsBackToScalar() {
        ClassLoader poisoned = new ClassLoader(DensityMapDispatcher.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("xyz.blanchot.vectorx.kernel.simd.SimdDensityMapKernels")) {
                    throw new ClassNotFoundException("simulated load failure: " + name);
                }
                return super.loadClass(name, resolve);
            }
        };

        DensityMapDispatcher dispatcher = new DensityMapDispatcher(VectorXConfig.defaults(), VectorXLog.noop(), poisoned);
        assertFalse(dispatcher.isVector());
        assertNotNull(dispatcher.disableReason());
    }

    @Test
    void perKernelScalarConfigDisablesOnlyThisKernel() {
        VectorXConfig config =
                VectorXConfig.defaults().withKernelMode(DensityMapDispatcher.CONFIG_KEY, KernelMode.SCALAR);
        DensityMapDispatcher dispatcher = new DensityMapDispatcher(config, VectorXLog.noop());

        assertFalse(dispatcher.isVector());
        assertNotNull(dispatcher.disableReason());
    }

    @Test
    void backendForcedScalarConfigDisablesVector() {
        VectorXConfig config = VectorXConfig.defaults().withBackendForcedScalar(true);
        DensityMapDispatcher dispatcher = new DensityMapDispatcher(config, VectorXLog.noop());

        assertFalse(dispatcher.isVector());
    }
}
