package com.google.workspace.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Handles authentication via gws CLI and registration with Claude Code.
 *
 * --install flow:
 * 1. Check gws on PATH, print install instructions if missing
 * 2. Run `gws auth setup` (interactive, creates GCP project + enables APIs)
 * 3. Run `gws auth login --scopes drive,gmail,calendar,chat` (browser OAuth)
 * 4. Register with Claude Code: `claude mcp add --scope user --transport stdio google-workspace -- java -jar <jarPath>`
 * 5. Print success/manual instructions
 *
 * --auth flow:
 * 1. Run `gws auth login --scopes drive,gmail,calendar,chat`
 */
public final class GwsAuth {

    private static final Logger log = LoggerFactory.getLogger(GwsAuth.class);
    private static final String MCP_SERVER_NAME = "google-workspace";
    private static final String GWS_SCOPES = "drive,gmail,calendar,chat";

    private GwsAuth() {}

    /**
     * Full install flow: check gws, auth, register with Claude Code.
     */
    static void install(String claudeBinary) throws IOException, InterruptedException {
        System.err.println("Google Workspace MCP Server — Setup");
        System.err.println("====================================");
        System.err.println();

        // 1. Check gws is available
        String gwsBinary = findGwsBinary();
        if (gwsBinary == null) {
            System.err.println("[error] gws CLI not found on PATH.");
            System.err.println();
            System.err.println("Install it with:");
            System.err.println("  npm install -g @googleworkspace/cli");
            System.err.println();
            System.err.println("Then re-run: java -jar google-workspace-mcp.jar --install");
            return;
        }
        System.err.println("[ok] Found gws CLI: " + gwsBinary);

        // 2. Run gws auth setup
        System.err.println();
        System.err.println("Step 1: Setting up Google Cloud project...");
        System.err.println("This will open your browser to configure OAuth credentials.");
        System.err.println();
        int setupExit = runInteractive(gwsBinary, "auth", "setup");
        if (setupExit != 0) {
            System.err.println("[warn] gws auth setup exited with code " + setupExit);
            System.err.println("You may need to run it manually: gws auth setup");
        }

        // 3. Run gws auth login
        System.err.println();
        System.err.println("Step 2: Authenticating with Google...");
        int loginExit = runInteractive(gwsBinary, "auth", "login", "--scopes", GWS_SCOPES);
        if (loginExit != 0) {
            System.err.println("[warn] gws auth login exited with code " + loginExit);
            System.err.println("You may need to run it manually: gws auth login --scopes " + GWS_SCOPES);
        }

        // 4. Register with Claude Code
        System.err.println();
        registerWithClaude(claudeBinary);

        System.err.println();
        System.err.println("Done! Restart Claude Code to use the Google Workspace tools.");
    }

    /**
     * Re-authenticate only (no setup, no registration).
     */
    static void authenticate() throws IOException, InterruptedException {
        String gwsBinary = findGwsBinary();
        if (gwsBinary == null) {
            System.err.println("[error] gws CLI not found. Install with: npm install -g @googleworkspace/cli");
            return;
        }

        System.err.println("Authenticating with Google...");
        int exit = runInteractive(gwsBinary, "auth", "login", "--scopes", GWS_SCOPES);
        if (exit == 0) {
            System.err.println("Authentication successful.");
        } else {
            System.err.println("Authentication failed (exit " + exit + ").");
        }
    }

    /**
     * Find gws binary on PATH.
     */
    static String findGwsBinary() {
        String[] candidates = {
                "gws",
                System.getProperty("user.home") + "/.local/bin/gws",
                "/usr/local/bin/gws",
                "/usr/bin/gws"
        };

        for (String candidate : candidates) {
            try {
                var process = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start();
                process.getInputStream().readAllBytes();
                int exit = process.waitFor();
                if (exit == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Find Claude Code binary on PATH.
     */
    static String findClaudeBinary() {
        String[] candidates = {
                "claude",
                System.getProperty("user.home") + "/.local/bin/claude",
                "/usr/local/bin/claude",
                "/usr/bin/claude"
        };

        for (String candidate : candidates) {
            try {
                var process = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start();
                process.getInputStream().readAllBytes();
                int exit = process.waitFor();
                if (exit == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Check if already registered with Claude Code.
     */
    static boolean isAlreadyRegistered(String claudeBinary) {
        try {
            var process = new ProcessBuilder(claudeBinary, "mcp", "list")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return output.contains(MCP_SERVER_NAME + ":");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolve the path to this JAR file.
     */
    static String resolveJarPath() {
        String jarPath = GwsAuth.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
        if (jarPath.endsWith(".jar")) {
            return Path.of(jarPath).toAbsolutePath().toString();
        }
        return Path.of("target", "google-workspace-mcp-1.0.0.jar").toAbsolutePath().toString();
    }

    private static void registerWithClaude(String claudeBinary)
            throws IOException, InterruptedException {
        if (claudeBinary == null || claudeBinary.isBlank()) {
            claudeBinary = findClaudeBinary();
        }

        if (claudeBinary == null) {
            System.err.println("[skip] Claude Code binary not found. Register manually:");
            printManualRegistration();
            return;
        }

        if (isAlreadyRegistered(claudeBinary)) {
            System.err.println("[skip] Already registered with Claude Code");
            return;
        }

        String jarPath = resolveJarPath();
        System.err.println("Registering with Claude Code...");

        var process = new ProcessBuilder(
                claudeBinary, "mcp", "add",
                "--scope", "user",
                "--transport", "stdio",
                MCP_SERVER_NAME, "--",
                "java", "-jar", jarPath)
                .inheritIO()
                .start();

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            System.err.println("[done] Registered as '" + MCP_SERVER_NAME + "'");
        } else {
            System.err.println("[fail] Registration failed (exit " + exitCode + "). Register manually:");
            printManualRegistration();
        }
    }

    private static void printManualRegistration() {
        String jarPath = resolveJarPath();
        System.err.println("  claude mcp add --scope user --transport stdio google-workspace -- \\");
        System.err.println("    java -jar " + jarPath);
    }

    private static int runInteractive(String... command) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command)
                .inheritIO()
                .start();
        return process.waitFor();
    }

    static String getMcpServerName() {
        return MCP_SERVER_NAME;
    }
}
