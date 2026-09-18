package xyz.blanchot.vectorx.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorModuleProbeTest {

    @Test
    void moduleIsPresentInThisTestJvmBecauseAddModulesIsConfigured() {
        assertTrue(VectorModuleProbe.isModuleInBootLayer(), "the test task must run with --add-modules=jdk.incubator.vector (see build.gradle)");
    }

    @Test
    void probeClassResolvesViaSystemClassLoader() {
        assertTrue(VectorModuleProbe.canResolveProbeClass(ClassLoader.getSystemClassLoader()));
    }

    @Test
    void unresolvableUnderAClassLoaderThatCannotSeeItReturnsFalse() {
        ClassLoader neverResolves = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };
        assertFalse(VectorModuleProbe.canResolveProbeClass(neverResolves));
    }
}
