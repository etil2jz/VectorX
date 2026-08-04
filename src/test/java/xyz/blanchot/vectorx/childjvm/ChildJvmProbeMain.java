package xyz.blanchot.vectorx.childjvm;

import xyz.blanchot.vectorx.VectorXConfig;
import xyz.blanchot.vectorx.diag.VectorXLog;
import xyz.blanchot.vectorx.dispatch.PackedBitsDispatcher;
import xyz.blanchot.vectorx.kernel.PackedBitsKernels;

import java.util.Arrays;

/**
 * Entry point spawned in a child JVM by
 * {@code xyz.blanchot.vectorx.ChildJvmScalarPathTest} WITHOUT
 * {@code --add-modules=jdk.incubator.vector}, to prove the scalar path
 * actually works standalone -- not just "the tests we wrote for it pass in
 * the same JVM that has the module enabled". Touches only kernel/dispatch/
 * config classes, none of which depend on Minecraft, fabric-loader or SLF4J.
 */
public final class ChildJvmProbeMain {

    private ChildJvmProbeMain() {
    }

    static void main() {
        VectorXConfig config = VectorXConfig.defaults();
        PackedBitsDispatcher dispatcher = new PackedBitsDispatcher(config, VectorXLog.console());
        System.out.println("backend=" + (dispatcher.isVector() ? "vector" : "scalar"));

        PackedBitsKernels kernels = dispatcher.backend();
        int[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        long[] packed = new long[4];
        kernels.pack(values, 4, values.length, packed);
        int[] roundTrip = new int[values.length];
        kernels.unpack(packed, 4, values.length, roundTrip);
        System.out.println("roundTrip=" + Arrays.equals(values, roundTrip));
    }
}
