package xyz.blanchot.vectorx;

import org.junit.jupiter.api.Test;
import xyz.blanchot.vectorx.childjvm.ChildJvmProbeMain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChildJvmScalarPathTest {

    private static Path classLocationOf(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static String readAll(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    @Test
    void scalarPathWorksWithoutAddModules() throws IOException, InterruptedException, URISyntaxException {
        Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
        Path mainClasses = classLocationOf(VectorXConfig.class);
        Path testClasses = classLocationOf(ChildJvmProbeMain.class);
        String classpath = mainClasses + java.io.File.pathSeparator + testClasses;

        ProcessBuilder processBuilder = new ProcessBuilder(javaBin.toString(), "-cp", classpath, ChildJvmProbeMain.class.getName());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = readAll(process);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "child JVM exited abnormally, output was:\n" + output);
        assertTrue(output.contains("backend=scalar"), "expected the scalar backend without --add-modules, got:\n" + output);
        assertTrue(output.contains("roundTrip=true"), "expected a correct scalar kernel result, got:\n" + output);
    }
}
