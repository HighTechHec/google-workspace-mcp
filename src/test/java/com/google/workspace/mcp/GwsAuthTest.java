package com.google.workspace.mcp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GwsAuthTest {

    // ── resolveJarPath ────────────────────────────────────────────

    @Nested
    class ResolveJarPathTests {

        @Test
        void returnsNonNull() {
            String path = GwsAuth.resolveJarPath();
            assertNotNull(path);
        }

        @Test
        void returnsAbsolutePath() {
            String path = GwsAuth.resolveJarPath();
            assertTrue(path.startsWith("/"), "Expected absolute path, got: " + path);
        }

        @Test
        void fallbackPathContainsArtifactName() {
            // When not running from a JAR (i.e. during tests), the fallback is used
            String path = GwsAuth.resolveJarPath();
            assertTrue(path.contains("google-workspace-mcp"),
                    "Expected fallback path containing 'google-workspace-mcp', got: " + path);
        }
    }

    // ── getMcpServerName ──────────────────────────────────────────

    @Nested
    class GetMcpServerNameTests {

        @Test
        void returnsExpectedName() {
            assertEquals("google-workspace", GwsAuth.getMcpServerName());
        }
    }

    // ── findGwsBinary ─────────────────────────────────────────────

    @Nested
    class FindGwsBinaryTests {

        @Test
        void isCallableAndHandlesGracefully() {
            // May return null or a path depending on the environment;
            // the key assertion is that it does not throw.
            String result = GwsAuth.findGwsBinary();
            // result is either null (not installed) or a non-empty string
            if (result != null) {
                assertFalse(result.isBlank());
            }
        }
    }

    // ── findClaudeBinary ──────────────────────────────────────────

    @Nested
    class FindClaudeBinaryTests {

        @Test
        void isCallableAndHandlesGracefully() {
            // May return null or a path depending on the environment;
            // the key assertion is that it does not throw.
            String result = GwsAuth.findClaudeBinary();
            if (result != null) {
                assertFalse(result.isBlank());
            }
        }
    }

    // ── isAlreadyRegistered ───────────────────────────────────────

    @Nested
    class IsAlreadyRegisteredTests {

        @Test
        void returnsFalseForNonexistentBinary() {
            boolean registered = GwsAuth.isAlreadyRegistered("/nonexistent/binary/claude-fake-12345");
            assertFalse(registered);
        }

        @Test
        void returnsFalseForNullBinary() {
            // Passing null should not throw; it should return false gracefully
            boolean registered = GwsAuth.isAlreadyRegistered(null);
            assertFalse(registered);
        }
    }
}
