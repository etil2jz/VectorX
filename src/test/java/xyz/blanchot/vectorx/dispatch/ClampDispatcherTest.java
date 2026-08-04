package xyz.blanchot.vectorx.dispatch;

import org.junit.jupiter.api.Test;
import xyz.blanchot.vectorx.VectorXConfig;
import xyz.blanchot.vectorx.VectorXConfig.KernelMode;
import xyz.blanchot.vectorx.diag.VectorXLog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClampDispatcherTest {

    @Test
    void resolvesToVectorByDefaultOnThisTestJvm() {
        ClampDispatcher dispatcher = new ClampDispatcher(VectorXConfig.defaults(), VectorXLog.noop());
        assertTrue(dispatcher.isVector());
        assertNull(dispatcher.disableReason());
    }

    @Test
    void forceScalarSystemPropertyDisablesVector() {
        System.setProperty(VectorModuleProbe.FORCE_SCALAR_PROPERTY, "true");
        try {
            ClampDispatcher dispatcher = new ClampDispatcher(VectorXConfig.defaults(), VectorXLog.noop());
            assertFalse(dispatcher.isVector());
            assertNotNull(dispatcher.disableReason());
        } finally {
            System.clearProperty(VectorModuleProbe.FORCE_SCALAR_PROPERTY);
        }
    }

    @Test
    void vectorBackendLoadFailureFallsBackToScalar() {
        ClassLoader poisoned = new ClassLoader(ClampDispatcher.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("xyz.blanchot.vectorx.kernel.simd.SimdClampKernels")) {
                    throw new ClassNotFoundException("simulated load failure: " + name);
                }
                return super.loadClass(name, resolve);
            }
        };

        ClampDispatcher dispatcher = new ClampDispatcher(VectorXConfig.defaults(), VectorXLog.noop(), poisoned);
        assertFalse(dispatcher.isVector());
        assertNotNull(dispatcher.disableReason());
    }

    @Test
    void perKernelScalarConfigDisablesOnlyThisKernel() {
        VectorXConfig config = VectorXConfig.defaults().withKernelMode(ClampDispatcher.CONFIG_KEY, KernelMode.SCALAR);
        ClampDispatcher dispatcher = new ClampDispatcher(config, VectorXLog.noop());

        assertFalse(dispatcher.isVector());
        assertNotNull(dispatcher.disableReason());
    }

    @Test
    void backendForcedScalarConfigDisablesVector() {
        VectorXConfig config = VectorXConfig.defaults().withBackendForcedScalar(true);
        ClampDispatcher dispatcher = new ClampDispatcher(config, VectorXLog.noop());

        assertFalse(dispatcher.isVector());
    }
}
