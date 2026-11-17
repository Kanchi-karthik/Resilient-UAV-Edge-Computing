package org.fog.test.proposed.graphs;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * GraphScriptLauncher
 * ===================
 * Utility wrapper around ProcessBuilder.
 *
 * Responsibilities:
 *   - Write Python script content to disk
 *   - Check that Python is callable
 *   - Check that required packages (matplotlib, seaborn, pandas, numpy) are installed
 *   - Execute the script synchronously and return the exit code
 *   - Print friendly install instructions if anything is missing
 *
 * @author Proposed Hybrid Framework — UAV-MEC Research
 */
public final class GraphScriptLauncher {

    private GraphScriptLauncher() { }

    // ── Dependency check ────────────────────────────────────────────────────────

    /**
     * Returns true if Python is reachable and all required packages are installed.
     * Prints actionable instructions to stdout if anything is missing.
     */
    public static boolean checkEnvironment() {
        String py = GraphConstants.getPythonCommand();

        // 1. Python reachable?
        try {
            Process p = new ProcessBuilder(py, "--version").start();
            p.waitFor();
            if (p.exitValue() != 0) {
                printMissingPython(py);
                return false;
            }
        } catch (Exception e) {
            printMissingPython(py);
            return false;
        }

        // 2. Packages installed?
        String check = "import matplotlib, seaborn, pandas, numpy; print('OK')";
        try {
            Process p = new ProcessBuilder(py, "-c", check).start();
            p.waitFor();
            if (p.exitValue() != 0) {
                printMissingPackages();
                return false;
            }
        } catch (Exception e) {
            printMissingPackages();
            return false;
        }

        return true;
    }

    // ── Script write ────────────────────────────────────────────────────────────

    /**
     * Writes script content to the given path and marks it executable (Unix).
     */
    public static void writeScript(String destinationPath, String content) throws IOException {
        Files.write(Paths.get(destinationPath), content.getBytes("UTF-8"));
        new File(destinationPath).setExecutable(true);
    }

    // ── Script execution ────────────────────────────────────────────────────────

    /**
     * Executes a Python script with the given arguments.
     * Streams stdout/stderr to the parent process console.
     *
     * @param scriptPath  absolute/relative path to the .py file
     * @param extraArgs   additional CLI arguments passed after the script path
     * @return            process exit code (0 = success)
     */
    public static int execute(String scriptPath, String... extraArgs) {
        String py = GraphConstants.getPythonCommand();
        String[] cmd = new String[2 + extraArgs.length];
        cmd[0] = py;
        cmd[1] = scriptPath;
        for (int i = 0; i < extraArgs.length; i++) {
            cmd[2 + i] = extraArgs[i];
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process proc = pb.start();
            return proc.waitFor();
        } catch (Exception e) {
            System.err.println("  [GraphScriptLauncher] Execution error: " + e.getMessage());
            return -1;
        }
    }

    // ── Friendly error messages ─────────────────────────────────────────────────

    private static void printMissingPython(String py) {
        System.err.println();
        System.err.println("  ╔══════════════════════════════════════════════════════════╗");
        System.err.println("  ║  GRAPH GENERATION SKIPPED — Python not found             ║");
        System.err.println("  ╠══════════════════════════════════════════════════════════╣");
        System.err.println("  ║  Tried command : " + py);
        System.err.println("  ║  Fix           : Install Python 3.8+ and add to PATH.    ║");
        System.err.println("  ╚══════════════════════════════════════════════════════════╝");
        System.err.println();
    }

    private static void printMissingPackages() {
        System.err.println();
        System.err.println("  ╔══════════════════════════════════════════════════════════╗");
        System.err.println("  ║  GRAPH GENERATION SKIPPED — Missing Python packages      ║");
        System.err.println("  ╠══════════════════════════════════════════════════════════╣");
        System.err.println("  ║  Run this once:                                          ║");
        System.err.println("  ║    pip install matplotlib seaborn pandas numpy           ║");
        System.err.println("  ╚══════════════════════════════════════════════════════════╝");
        System.err.println();
    }

    // ── PrintWriter helper ──────────────────────────────────────────────────────

    /**
     * Opens a PrintWriter for writing a script file line by line.
     */
    public static PrintWriter openScriptWriter(String path) throws IOException {
        return new PrintWriter(new java.io.FileWriter(path, false));
    }
}
