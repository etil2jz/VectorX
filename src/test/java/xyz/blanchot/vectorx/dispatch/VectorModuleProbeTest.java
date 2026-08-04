package xyz.blanchot.vectorx.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorModuleProbeTest {

    @Test
    void moduleIsPresentInThisTestJvmBecauseAddModulesIsConfigured() {
        assertTrue(VectorModuleProbe.isModuleInBootLayer(),
                "the test task must run with --add-modules=jdk.incubator.vector (see build.gradle)");
    }

    @Test
    void probeClassResolvesViaSystemClassLoader() {
        assertTrue(VectorModuleProbe.canResolveProbeClass(ClassLoader.getSystemClassLoader()));
    }

    @Test
    void unresolvableUnderAClassLoaderThatCannotSeeItReturnsFalse() {
        // Overrides every entry point Class.forName could reach (loadClass(String),
        // loadClass(String,boolean) and findClass) so this is guaranteed to fail
        // regardless of JDK-internal delegation details. A parent-only classloader
        // is NOT a reliable negative case here: on this JDK, and in this process
        // (started with --add-modules=jdk.incubator.vector), jdk.incubator.vector
        // turned out to be reachable even through a bootstrap-rooted delegate. That
        // says nothing about Fabric's Knot classloader specifically -- see the
        // README's "Limites connues" -- it only means a parent-only classloader is
        // not a valid way to construct this test's negative case.
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
