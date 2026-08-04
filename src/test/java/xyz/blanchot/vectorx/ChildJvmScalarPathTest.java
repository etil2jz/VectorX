package xyz.blanchot.vectorx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import xyz.blanchot.vectorx.childjvm.ChildJvmProbeMain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one test that proves the mod's scalar path genuinely works standalone,
 * not merely "under a test JVM that happens to have the incubator module
 * enabled". Every other JUnit test in this project runs in the same JVM as
 * the test task, which always carries {@code --add-modules=jdk.incubator.vector}
 * (see build.gradle) -- so none of them would catch a regression that broke
 * the no-module fallback path.
 *
 * <p>This spawns a genuinely separate JVM, deliberately WITHOUT that flag,
 * running {@link ChildJvmProbeMain}, and asserts it exits cleanly, reports
 * {@code backend=scalar}, and still produces a correct kernel result.
 */
class ChildJvmScalarPathTest {

	@Test
	void scalarPathWorksWithoutAddModules() throws IOException, InterruptedException, URISyntaxException {
		Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
		Path mainClasses = classLocationOf(VectorXConfig.class);
		Path testClasses = classLocationOf(ChildJvmProbeMain.class);
		String classpath = mainClasses + java.io.File.pathSeparator + testClasses;

		ProcessBuilder processBuilder = new ProcessBuilder(
				javaBin.toString(),
				"-cp", classpath,
				ChildJvmProbeMain.class.getName());
		processBuilder.redirectErrorStream(true);

		Process process = processBuilder.start();
		String output = readAll(process);
		int exitCode = process.waitFor();

		assertEquals(0, exitCode, "child JVM exited abnormally, output was:\n" + output);
		assertTrue(output.contains("backend=scalar"),
				"expected the scalar backend without --add-modules, got:\n" + output);
		assertTrue(output.contains("roundTrip=true"),
				"expected a correct scalar kernel result, got:\n" + output);
	}

	private static Path classLocationOf(Class<?> type) throws URISyntaxException {
		return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

	private static String readAll(Process process) throws IOException {
		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append('\n');
			}
		}
		return output.toString();
	}
}
