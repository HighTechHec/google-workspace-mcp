package com.google.workspace.mcp;

import com.google.workspace.mcp.GwsClient.GwsResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GwsClientTest {

    // ── GwsResult ────────────────────────────────────────────────

    @Nested
    class GwsResultTests {

        @Test
        void isSuccess_returnsTrueForExitCodeZero() {
            GwsResult result = new GwsResult(0, "output", "");
            assertTrue(result.isSuccess());
        }

        @Test
        void isSuccess_returnsFalseForNonZeroExitCode() {
            GwsResult result = new GwsResult(1, "", "error");
            assertFalse(result.isSuccess());
        }

        @Test
        void isSuccess_returnsFalseForNegativeExitCode() {
            GwsResult result = new GwsResult(-1, "", "killed");
            assertFalse(result.isSuccess());
        }

        @Test
        void recordAccessors_returnStoredValues() {
            GwsResult result = new GwsResult(42, "out", "err");
            assertEquals(42, result.exitCode());
            assertEquals("out", result.stdout());
            assertEquals("err", result.stderr());
        }
    }

    // ── paramsJson ───────────────────────────────────────────────

    @Nested
    class ParamsJsonTests {

        @Test
        void serializesSimpleMap() {
            String json = GwsClient.paramsJson(Map.of("key", "value"));
            assertEquals("{\"key\":\"value\"}", json);
        }

        @Test
        void serializesEmptyMap() {
            String json = GwsClient.paramsJson(Map.of());
            assertEquals("{}", json);
        }

        @Test
        void serializesNestedValues() {
            Map<String, Object> params = Map.of(
                    "filter", Map.of("status", "active")
            );
            String json = GwsClient.paramsJson(params);
            assertTrue(json.contains("\"filter\""));
            assertTrue(json.contains("\"status\":\"active\""));
        }

        @Test
        void serializesNumericValues() {
            Map<String, Object> params = Map.of("limit", 10);
            String json = GwsClient.paramsJson(params);
            assertEquals("{\"limit\":10}", json);
        }
    }

    // ── bodyJson ─────────────────────────────────────────────────

    @Nested
    class BodyJsonTests {

        @Test
        void serializesSimpleMap() {
            String json = GwsClient.bodyJson(Map.of("name", "test"));
            assertEquals("{\"name\":\"test\"}", json);
        }

        @Test
        void serializesArrayValues() {
            Map<String, Object> body = Map.of("items", List.of("a", "b", "c"));
            String json = GwsClient.bodyJson(body);
            assertTrue(json.contains("\"items\""));
            assertTrue(json.contains("[\"a\",\"b\",\"c\"]"));
        }

        @Test
        void serializesEmptyMap() {
            String json = GwsClient.bodyJson(Map.of());
            assertEquals("{}", json);
        }
    }

    // ── Constructor ──────────────────────────────────────────────

    @Nested
    class ConstructorTests {

        @Test
        void defaultConstructor_usesGwsAndDefaultTimeout() {
            GwsClient client = new GwsClient();
            assertEquals("gws", client.getGwsBinary());
            assertEquals(30, client.getTimeoutSeconds());
        }

        @Test
        void customConstructor_storesValues() {
            GwsClient client = new GwsClient("/usr/local/bin/gws", 60);
            assertEquals("/usr/local/bin/gws", client.getGwsBinary());
            assertEquals(60, client.getTimeoutSeconds());
        }
    }

    // ── isAvailable ──────────────────────────────────────────────

    @Nested
    class IsAvailableTests {

        @Test
        void returnsTrue_whenBinarySucceeds() {
            // /bin/true ignores arguments and always exits 0
            GwsClient client = new GwsClient("/bin/true", 5);
            assertTrue(client.isAvailable());
        }

        @Test
        void returnsFalse_whenBinaryFails() {
            // /bin/false ignores arguments and always exits 1
            GwsClient client = new GwsClient("/bin/false", 5);
            assertFalse(client.isAvailable());
        }

        @Test
        void returnsFalse_whenBinaryNotFound() {
            GwsClient client = new GwsClient("/nonexistent/binary/gws-fake-12345", 5);
            assertFalse(client.isAvailable());
        }
    }

    // ── execute ──────────────────────────────────────────────────

    @Nested
    class ExecuteTests {

        @Test
        void capturesStdout() throws Exception {
            GwsClient client = new GwsClient("/bin/sh", 5);

            GwsResult result = client.execute(List.of("-c", "echo 'hello world'"));
            assertEquals(0, result.exitCode());
            assertEquals("hello world", result.stdout());
            assertEquals("", result.stderr());
        }

        @Test
        void capturesStderr() throws Exception {
            GwsClient client = new GwsClient("/bin/sh", 5);

            GwsResult result = client.execute(List.of("-c", "echo 'error msg' >&2; exit 1"));
            assertEquals(1, result.exitCode());
            assertEquals("error msg", result.stderr());
        }

        @Test
        void capturesBothStreams() throws Exception {
            GwsClient client = new GwsClient("/bin/sh", 5);

            GwsResult result = client.execute(List.of("-c", "echo 'stdout line'; echo 'stderr line' >&2"));
            assertEquals(0, result.exitCode());
            assertEquals("stdout line", result.stdout());
            assertEquals("stderr line", result.stderr());
        }

        @Test
        void passesArguments() throws Exception {
            // Use /bin/echo which prints all its arguments
            GwsClient client = new GwsClient("/bin/echo", 5);

            GwsResult result = client.execute(List.of("arg1", "arg2", "arg3"));
            assertEquals(0, result.exitCode());
            assertEquals("arg1 arg2 arg3", result.stdout());
        }

        @Test
        void capturesNonZeroExitCode() throws Exception {
            GwsClient client = new GwsClient("/bin/sh", 5);

            GwsResult result = client.execute(List.of("-c", "exit 42"));
            assertEquals(42, result.exitCode());
            assertFalse(result.isSuccess());
        }

        @Test
        void trimsWhitespace() throws Exception {
            GwsClient client = new GwsClient("/bin/sh", 5);

            GwsResult result = client.execute(List.of("-c", "echo '  padded  '"));
            assertEquals("padded", result.stdout());
        }

        @Test
        void throwsIOException_whenBinaryNotFound() {
            GwsClient client = new GwsClient("/nonexistent/binary/gws-fake-12345", 5);
            assertThrows(IOException.class, () -> client.execute(List.of("--version")));
        }

        @Test
        void handlesEmptyOutput() throws Exception {
            GwsClient client = new GwsClient("/bin/true", 5);

            GwsResult result = client.execute(List.of());
            assertEquals(0, result.exitCode());
            assertEquals("", result.stdout());
            assertEquals("", result.stderr());
        }

        @Test
        void handlesMultilineOutput() throws Exception {
            GwsClient client = new GwsClient("/bin/sh", 5);

            GwsResult result = client.execute(List.of("-c", "echo 'line1'; echo 'line2'; echo 'line3'"));
            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("line1"));
            assertTrue(result.stdout().contains("line2"));
            assertTrue(result.stdout().contains("line3"));
        }
    }

    // ── executeJson ──────────────────────────────────────────────

    @Nested
    class ExecuteJsonTests {

        @Test
        void returnsStdout_onSuccess() throws Exception {
            GwsClient client = new GwsClient("/bin/sh", 5);

            String json = client.executeJson(List.of("-c", "echo '{\"id\":\"123\"}'"));
            assertEquals("{\"id\":\"123\"}", json);
        }

        @Test
        void throwsIOException_onFailure() {
            GwsClient client = new GwsClient("/bin/sh", 5);

            IOException ex = assertThrows(IOException.class,
                    () -> client.executeJson(List.of("-c", "echo 'not found' >&2; exit 1")));
            assertTrue(ex.getMessage().contains("exit 1"), "Should contain exit code");
            assertTrue(ex.getMessage().contains("not found"), "Should contain stderr");
        }

        @Test
        void throwsIOException_containsStderr() {
            GwsClient client = new GwsClient("/bin/sh", 5);

            IOException ex = assertThrows(IOException.class,
                    () -> client.executeJson(List.of("-c", "echo 'detailed error message' >&2; exit 2")));
            assertTrue(ex.getMessage().contains("detailed error message"), "Should contain stderr message");
            assertTrue(ex.getMessage().contains("exit 2"), "Should contain exit code");
        }

        @Test
        void throwsIOException_whenBinaryNotFound() {
            GwsClient client = new GwsClient("/nonexistent/binary/gws-fake-12345", 5);
            assertThrows(IOException.class, () -> client.executeJson(List.of()));
        }
    }

    // ── Timeout ──────────────────────────────────────────────────

    @Nested
    class TimeoutTests {

        @Test
        void throwsIOException_onTimeout() {
            GwsClient client = new GwsClient("/bin/sh", 1);

            IOException ex = assertThrows(IOException.class,
                    () -> client.execute(List.of("-c", "sleep 30")));
            assertTrue(ex.getMessage().contains("timed out"),
                    "Expected 'timed out' in message but got: " + ex.getMessage());
        }
    }
}
