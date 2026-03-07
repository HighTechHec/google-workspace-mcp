package com.google.workspace.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GwsClient {

    private static final Logger log = LoggerFactory.getLogger(GwsClient.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String gwsBinary;
    private final long timeoutSeconds;

    public GwsClient() {
        this("gws", DEFAULT_TIMEOUT_SECONDS);
    }

    public GwsClient(String gwsBinary, long timeoutSeconds) {
        this.gwsBinary = gwsBinary;
        this.timeoutSeconds = timeoutSeconds;
    }

    public record GwsResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    /**
     * Execute a gws command and return raw result.
     * Reads stdout and stderr concurrently to prevent deadlock.
     */
    public GwsResult execute(List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(gwsBinary);
        command.addAll(args);

        log.debug("Executing: {}", command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().remove("TERM");
        Process process = pb.start();

        // Read streams concurrently to prevent deadlock
        CompletableFuture<byte[]> stdoutFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return process.getInputStream().readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });
        CompletableFuture<byte[]> stderrFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return process.getErrorStream().readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });

        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("gws command timed out after " + timeoutSeconds + " seconds");
        }

        String stdout = new String(stdoutFuture.join(), StandardCharsets.UTF_8);
        String stderr = new String(stderrFuture.join(), StandardCharsets.UTF_8);

        return new GwsResult(process.exitValue(), stdout.trim(), stderr.trim());
    }

    /**
     * Execute a gws command, throw on failure, return stdout JSON.
     */
    public String executeJson(List<String> args) throws IOException, InterruptedException {
        GwsResult result = execute(args);
        if (!result.isSuccess()) {
            throw new IOException("gws command failed (exit " + result.exitCode() + "): " + result.stderr());
        }
        return result.stdout();
    }

    /** Serialize a map to JSON string for --params flag. */
    public static String paramsJson(Map<String, Object> params) {
        try {
            return MAPPER.writeValueAsString(params);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize params", e);
        }
    }

    /** Serialize a map to JSON string for --json flag. */
    public static String bodyJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize body", e);
        }
    }

    /** Check if gws binary is available on PATH. */
    public boolean isAvailable() {
        try {
            GwsResult result = execute(List.of("--version"));
            return result.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    public String getGwsBinary() {
        return gwsBinary;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
