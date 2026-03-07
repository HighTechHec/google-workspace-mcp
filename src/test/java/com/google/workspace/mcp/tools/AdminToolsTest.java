package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminToolsTest {

    // ── StubGwsClient ─────────────────────────────────────────────

    /**
     * A GwsClient stub that captures the args passed to executeJson
     * and returns a canned response. No real process is spawned.
     */
    static class StubGwsClient extends GwsClient {
        List<String> lastArgs;
        String response = "{}";
        boolean shouldFail = false;
        String failMessage = "stub error";

        StubGwsClient() {
            super("/bin/false", 1);
        }

        @Override
        public String executeJson(List<String> args) throws IOException {
            lastArgs = List.copyOf(args);
            if (shouldFail) {
                throw new IOException(failMessage);
            }
            return response;
        }
    }

    private StubGwsClient stub;
    private List<SyncToolSpecification> specs;

    @BeforeEach
    void setUp() {
        stub = new StubGwsClient();
        specs = AdminTools.tools(stub);
    }

    // ── Tool registration ─────────────────────────────────────────

    @Test
    void tools_returnsThreeTools() {
        assertEquals(3, specs.size());
    }

    @Test
    void tools_haveCorrectNames() {
        List<String> names = specs.stream()
                .map(s -> s.tool().name())
                .toList();
        assertTrue(names.contains("admin_users_list"));
        assertTrue(names.contains("admin_users_get"));
        assertTrue(names.contains("admin_groups_list"));
    }

    // ── admin_users_list ──────────────────────────────────────────

    @Nested
    class AdminUsersListTests {

        private SyncToolSpecification spec;

        @BeforeEach
        void setUp() {
            spec = specs.stream()
                    .filter(s -> s.tool().name().equals("admin_users_list"))
                    .findFirst().orElseThrow();
        }

        @Test
        void passesSourcesAndReadMaskAndPageSize() {
            stub.response = "{\"people\":[]}";
            CallToolResult result = callTool(spec, Map.of("pageSize", 25));

            assertNotNull(stub.lastArgs);
            assertEquals("people", stub.lastArgs.get(0));
            assertEquals("people", stub.lastArgs.get(1));
            assertEquals("listDirectoryPeople", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("DIRECTORY_SOURCE_TYPE_DOMAIN_PROFILE"),
                    "Should contain directory source type");
            assertTrue(params.contains("\"readMask\":\"names,emailAddresses\""),
                    "Should contain readMask");
            assertTrue(params.contains("\"pageSize\":25"),
                    "Should contain pageSize 25");
        }

        @Test
        void usesDefaultPageSizeWhenNotProvided() {
            stub.response = "{\"people\":[]}";
            callTool(spec, Map.of());

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"pageSize\":50"),
                    "Should default pageSize to 50");
        }

        @Test
        void returnsSanitizedResult() {
            stub.response = "{\"people\":[{\"name\":\"Test\"}]}";
            CallToolResult result = callTool(spec, Map.of());

            // sanitizedResult produces 2 text contents: preamble + wrapped JSON
            assertEquals(2, result.content().size());
            TextContent preamble = (TextContent) result.content().get(0);
            assertTrue(preamble.text().contains("SECURITY CONTEXT"));
            TextContent wrapped = (TextContent) result.content().get(1);
            assertTrue(wrapped.text().contains("{\"people\":[{\"name\":\"Test\"}]}"));
        }

        @Test
        void returnsErrorOnFailure() {
            stub.shouldFail = true;
            stub.failMessage = "connection refused";
            CallToolResult result = callTool(spec, Map.of());

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("connection refused"));
        }
    }

    // ── admin_users_get ───────────────────────────────────────────

    @Nested
    class AdminUsersGetTests {

        private SyncToolSpecification spec;

        @BeforeEach
        void setUp() {
            spec = specs.stream()
                    .filter(s -> s.tool().name().equals("admin_users_get"))
                    .findFirst().orElseThrow();
        }

        @Test
        void passesResourceNameWithPrefix() {
            stub.response = "{\"resourceName\":\"people/12345\"}";
            callTool(spec, Map.of("resourceName", "people/12345"));

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"resourceName\":\"people/12345\""),
                    "Should pass resource name as-is when already prefixed");
            assertTrue(params.contains("\"personFields\":\"names,emailAddresses,phoneNumbers,organizations\""),
                    "Should contain personFields");
        }

        @Test
        void prefixesPeopleWhenMissing() {
            stub.response = "{\"resourceName\":\"people/67890\"}";
            callTool(spec, Map.of("resourceName", "67890"));

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"resourceName\":\"people/67890\""),
                    "Should prepend 'people/' when missing");
        }

        @Test
        void usesGetEndpoint() {
            stub.response = "{}";
            callTool(spec, Map.of("resourceName", "people/1"));

            assertEquals("people", stub.lastArgs.get(0));
            assertEquals("people", stub.lastArgs.get(1));
            assertEquals("get", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));
        }

        @Test
        void returnsErrorWhenResourceNameMissing() {
            CallToolResult result = callTool(spec, Map.of());

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("'resourceName' is required"));
        }

        @Test
        void returnsErrorWhenResourceNameBlank() {
            CallToolResult result = callTool(spec, Map.of("resourceName", "  "));

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("'resourceName' is required"));
        }

        @Test
        void returnsErrorOnFailure() {
            stub.shouldFail = true;
            stub.failMessage = "not found";
            CallToolResult result = callTool(spec, Map.of("resourceName", "people/1"));

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("not found"));
        }
    }

    // ── admin_groups_list ─────────────────────────────────────────

    @Nested
    class AdminGroupsListTests {

        private SyncToolSpecification spec;

        @BeforeEach
        void setUp() {
            spec = specs.stream()
                    .filter(s -> s.tool().name().equals("admin_groups_list"))
                    .findFirst().orElseThrow();
        }

        @Test
        void passesCorrectCommand() {
            stub.response = "{\"contactGroups\":[]}";
            callTool(spec, Map.of());

            assertNotNull(stub.lastArgs);
            assertEquals(List.of("people", "contactGroups", "list"), stub.lastArgs);
        }

        @Test
        void requiresNoArgs() {
            stub.response = "{\"contactGroups\":[]}";
            CallToolResult result = callTool(spec, Map.of());

            assertFalse(result.isError() != null && result.isError());
        }

        @Test
        void returnsSanitizedResult() {
            stub.response = "{\"contactGroups\":[{\"name\":\"Friends\"}]}";
            CallToolResult result = callTool(spec, Map.of());

            assertEquals(2, result.content().size());
            TextContent wrapped = (TextContent) result.content().get(1);
            assertTrue(wrapped.text().contains("Friends"));
        }

        @Test
        void returnsErrorOnFailure() {
            stub.shouldFail = true;
            stub.failMessage = "API error";
            CallToolResult result = callTool(spec, Map.of());

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("API error"));
        }
    }

    // ── Helper method tests ───────────────────────────────────────

    @Nested
    class HelperTests {

        @Test
        void parsePageSize_integer() {
            assertEquals(25, AdminTools.parsePageSize(Map.of("pageSize", 25)));
        }

        @Test
        void parsePageSize_string() {
            assertEquals(100, AdminTools.parsePageSize(Map.of("pageSize", "100")));
        }

        @Test
        void parsePageSize_invalidStringReturnsDefault() {
            assertEquals(50, AdminTools.parsePageSize(Map.of("pageSize", "abc")));
        }

        @Test
        void parsePageSize_missingReturnsDefault() {
            assertEquals(50, AdminTools.parsePageSize(Map.of()));
        }

        @Test
        void parsePageSize_nullReturnsDefault() {
            Map<String, Object> args = new HashMap<>();
            args.put("pageSize", null);
            assertEquals(50, AdminTools.parsePageSize(args));
        }

        @Test
        void requireString_validValue() {
            assertEquals("hello", AdminTools.requireString(Map.of("key", "hello"), "key"));
        }

        @Test
        void requireString_throwsOnMissing() {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> AdminTools.requireString(Map.of(), "key"));
            assertTrue(ex.getMessage().contains("'key' is required"));
        }

        @Test
        void requireString_throwsOnBlank() {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> AdminTools.requireString(Map.of("key", "   "), "key"));
            assertTrue(ex.getMessage().contains("'key' is required"));
        }

        @Test
        void requireString_throwsOnNull() {
            Map<String, Object> args = new HashMap<>();
            args.put("key", null);
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> AdminTools.requireString(args, "key"));
            assertTrue(ex.getMessage().contains("'key' is required"));
        }
    }

    // ── Test utility ──────────────────────────────────────────────

    private static CallToolResult callTool(SyncToolSpecification spec, Map<String, Object> args) {
        var request = new McpSchema.CallToolRequest(spec.tool().name(), args);
        return spec.callHandler().apply(null, request);
    }
}
