package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.GwsClient;
import com.google.workspace.mcp.GwsClient.GwsResult;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DriveToolsTest {

    private StubGwsClient stub;
    private DriveTools driveTools;

    @BeforeEach
    void setUp() {
        stub = new StubGwsClient();
        driveTools = new DriveTools(stub);
    }

    // ── tools() ─────────────────────────────────────────────────

    @Nested
    class ToolsTests {

        @Test
        void returnsExactlySixTools() {
            List<SyncToolSpecification> tools = driveTools.tools();
            assertEquals(6, tools.size());
        }

        @Test
        void toolNamesAreCorrect() {
            List<String> names = driveTools.tools().stream()
                    .map(t -> t.tool().name())
                    .toList();
            assertEquals(List.of(
                    "drive_files_list",
                    "drive_files_get",
                    "drive_files_download",
                    "drive_files_upload",
                    "drive_files_create_folder",
                    "drive_files_delete"
            ), names);
        }
    }

    // ── parsePageSize ───────────────────────────────────────────

    @Nested
    class ParsePageSizeTests {

        @Test
        void defaultsTo20ForNull() {
            assertEquals(20, DriveTools.parsePageSize(null));
        }

        @Test
        void clampsTo1ForZero() {
            assertEquals(1, DriveTools.parsePageSize(0));
        }

        @Test
        void clampsTo1ForNegative() {
            assertEquals(1, DriveTools.parsePageSize(-5));
        }

        @Test
        void clampsTo100ForLargeValue() {
            assertEquals(100, DriveTools.parsePageSize(200));
        }

        @Test
        void acceptsValidNumber() {
            assertEquals(50, DriveTools.parsePageSize(50));
        }

        @Test
        void parsesStringValue() {
            assertEquals(30, DriveTools.parsePageSize("30"));
        }

        @Test
        void defaultsTo20ForInvalidString() {
            assertEquals(20, DriveTools.parsePageSize("notanumber"));
        }

        @Test
        void clampsStringTo100() {
            assertEquals(100, DriveTools.parsePageSize("999"));
        }

        @Test
        void clampsStringTo1() {
            assertEquals(1, DriveTools.parsePageSize("-10"));
        }
    }

    // ── requireString ───────────────────────────────────────────

    @Nested
    class RequireStringTests {

        @Test
        void returnsValueForValidString() {
            assertEquals("hello", DriveTools.requireString(Map.of("key", "hello"), "key"));
        }

        @Test
        void throwsForNull() {
            var args = new java.util.HashMap<String, Object>();
            args.put("key", null);
            assertThrows(IllegalArgumentException.class,
                    () -> DriveTools.requireString(args, "key"));
        }

        @Test
        void throwsForMissingKey() {
            assertThrows(IllegalArgumentException.class,
                    () -> DriveTools.requireString(Map.of(), "key"));
        }

        @Test
        void throwsForBlankString() {
            assertThrows(IllegalArgumentException.class,
                    () -> DriveTools.requireString(Map.of("key", "   "), "key"));
        }

        @Test
        void throwsForNonString() {
            assertThrows(IllegalArgumentException.class,
                    () -> DriveTools.requireString(Map.of("key", 42), "key"));
        }
    }

    // ── safeArgs ────────────────────────────────────────────────

    @Nested
    class SafeArgsTests {

        @Test
        void returnsEmptyMapForNullArguments() {
            CallToolRequest request = new CallToolRequest("test", null);
            Map<String, Object> result = DriveTools.safeArgs(request);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void returnsArgumentsMapWhenPresent() {
            Map<String, Object> args = Map.of("key", "value");
            CallToolRequest request = new CallToolRequest("test", args);
            assertSame(args, DriveTools.safeArgs(request));
        }
    }

    // ── drive_files_list ────────────────────────────────────────

    @Nested
    class FilesListTests {

        @Test
        void buildsCorrectCommandWithDefaults() {
            stub.nextJsonResponse = "{\"files\":[]}";
            CallToolRequest request = new CallToolRequest("drive_files_list", Map.of());
            CallToolResult result = invokeHandler("drive_files_list", request);

            assertNotNull(stub.lastArgs);
            assertEquals("drive", stub.lastArgs.get(0));
            assertEquals("files", stub.lastArgs.get(1));
            assertEquals("list", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));
            assertTrue(stub.lastArgs.get(4).contains("\"pageSize\":20"));
        }

        @Test
        void includesQueryWhenProvided() {
            stub.nextJsonResponse = "{\"files\":[]}";
            CallToolRequest request = new CallToolRequest("drive_files_list",
                    Map.of("query", "name contains 'report'"));
            invokeHandler("drive_files_list", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"q\":\"name contains 'report'\""));
        }

        @Test
        void respectsPageSize() {
            stub.nextJsonResponse = "{\"files\":[]}";
            CallToolRequest request = new CallToolRequest("drive_files_list",
                    Map.of("pageSize", 50));
            invokeHandler("drive_files_list", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"pageSize\":50"));
        }

        @Test
        void returnsSanitizedResult() {
            stub.nextJsonResponse = "{\"files\":[{\"id\":\"abc\"}]}";
            CallToolRequest request = new CallToolRequest("drive_files_list", Map.of());
            CallToolResult result = invokeHandler("drive_files_list", request);

            // sanitizedResult produces 2 content items: preamble + wrapped
            assertEquals(2, result.content().size());
            TextContent preamble = (TextContent) result.content().get(0);
            assertTrue(preamble.text().contains("SECURITY CONTEXT"));
        }

        @Test
        void returnsErrorOnFailure() {
            stub.nextJsonResponse = null; // will throw
            CallToolRequest request = new CallToolRequest("drive_files_list", Map.of());
            CallToolResult result = invokeHandler("drive_files_list", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("drive_files_list failed"));
        }
    }

    // ── drive_files_get ─────────────────────────────────────────

    @Nested
    class FilesGetTests {

        @Test
        void buildsCorrectCommand() {
            stub.nextJsonResponse = "{\"id\":\"file123\",\"name\":\"test.txt\"}";
            CallToolRequest request = new CallToolRequest("drive_files_get",
                    Map.of("fileId", "file123"));
            invokeHandler("drive_files_get", request);

            assertEquals("drive", stub.lastArgs.get(0));
            assertEquals("files", stub.lastArgs.get(1));
            assertEquals("get", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));
            assertTrue(stub.lastArgs.get(4).contains("\"fileId\":\"file123\""));
        }

        @Test
        void returnsSanitizedResult() {
            stub.nextJsonResponse = "{\"id\":\"file123\"}";
            CallToolRequest request = new CallToolRequest("drive_files_get",
                    Map.of("fileId", "file123"));
            CallToolResult result = invokeHandler("drive_files_get", request);

            assertEquals(2, result.content().size());
        }

        @Test
        void returnsErrorWhenFileIdMissing() {
            CallToolRequest request = new CallToolRequest("drive_files_get", Map.of());
            CallToolResult result = invokeHandler("drive_files_get", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("'fileId' is required"));
        }
    }

    // ── drive_files_download ────────────────────────────────────

    @Nested
    class FilesDownloadTests {

        @Test
        void buildsCorrectCommand() {
            stub.nextExecuteResponse = new GwsResult(0, "", "");
            CallToolRequest request = new CallToolRequest("drive_files_download",
                    Map.of("fileId", "dl123"));
            invokeHandler("drive_files_download", request);

            assertEquals("drive", stub.lastArgs.get(0));
            assertEquals("files", stub.lastArgs.get(1));
            assertEquals("get", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));
            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"fileId\":\"dl123\""));
            assertTrue(params.contains("\"alt\":\"media\""));
            assertEquals("-o", stub.lastArgs.get(5));
            assertTrue(stub.lastArgs.get(6).contains("dl123"));
        }

        @Test
        void returnsPlainResult() {
            stub.nextExecuteResponse = new GwsResult(0, "", "");
            CallToolRequest request = new CallToolRequest("drive_files_download",
                    Map.of("fileId", "dl123"));
            CallToolResult result = invokeHandler("drive_files_download", request);

            // plainResult produces 1 content item
            assertEquals(1, result.content().size());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("\"savedTo\""));
            assertTrue(content.text().contains("\"fileId\":\"dl123\""));
        }

        @Test
        void returnsErrorWhenFileIdMissing() {
            CallToolRequest request = new CallToolRequest("drive_files_download", Map.of());
            CallToolResult result = invokeHandler("drive_files_download", request);

            assertTrue(result.isError());
        }
    }

    // ── drive_files_upload ──────────────────────────────────────

    @Nested
    class FilesUploadTests {

        @Test
        void buildsCorrectCommandWithPathOnly() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("drive_files_upload",
                    Map.of("filePath", "/tmp/test.txt"));
            invokeHandler("drive_files_upload", request);

            assertEquals("drive", stub.lastArgs.get(0));
            assertEquals("files", stub.lastArgs.get(1));
            assertEquals("create", stub.lastArgs.get(2));
            assertEquals("--upload", stub.lastArgs.get(3));
            assertEquals("/tmp/test.txt", stub.lastArgs.get(4));
        }

        @Test
        void includesNameAndParentWhenProvided() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("drive_files_upload",
                    Map.of("filePath", "/tmp/test.txt", "name", "report.pdf", "parentId", "folder456"));
            invokeHandler("drive_files_upload", request);

            assertTrue(stub.lastArgs.contains("--json"));
            assertTrue(stub.lastArgs.contains("--upload"));
            assertTrue(stub.lastArgs.contains("/tmp/test.txt"));
            // Find the JSON body arg
            int jsonIdx = stub.lastArgs.indexOf("--json");
            String body = stub.lastArgs.get(jsonIdx + 1);
            assertTrue(body.contains("\"name\":\"report.pdf\""));
            assertTrue(body.contains("\"parents\":[\"folder456\"]"));
        }

        @Test
        void returnsPlainResult() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("drive_files_upload",
                    Map.of("filePath", "/tmp/test.txt"));
            CallToolResult result = invokeHandler("drive_files_upload", request);

            assertEquals(1, result.content().size());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("new123"));
        }

        @Test
        void returnsErrorWhenFilePathMissing() {
            CallToolRequest request = new CallToolRequest("drive_files_upload", Map.of());
            CallToolResult result = invokeHandler("drive_files_upload", request);

            assertTrue(result.isError());
        }
    }

    // ── drive_files_create_folder ───────────────────────────────

    @Nested
    class FilesCreateFolderTests {

        @Test
        void buildsCorrectCommand() {
            stub.nextJsonResponse = "{\"id\":\"folder789\"}";
            CallToolRequest request = new CallToolRequest("drive_files_create_folder",
                    Map.of("name", "My Folder"));
            invokeHandler("drive_files_create_folder", request);

            assertEquals("drive", stub.lastArgs.get(0));
            assertEquals("files", stub.lastArgs.get(1));
            assertEquals("create", stub.lastArgs.get(2));
            assertEquals("--json", stub.lastArgs.get(3));
            String body = stub.lastArgs.get(4);
            assertTrue(body.contains("\"name\":\"My Folder\""));
            assertTrue(body.contains("\"mimeType\":\"application/vnd.google-apps.folder\""));
        }

        @Test
        void includesParentWhenProvided() {
            stub.nextJsonResponse = "{\"id\":\"folder789\"}";
            CallToolRequest request = new CallToolRequest("drive_files_create_folder",
                    Map.of("name", "Sub Folder", "parentId", "parent123"));
            invokeHandler("drive_files_create_folder", request);

            String body = stub.lastArgs.get(4);
            assertTrue(body.contains("\"parents\":[\"parent123\"]"));
        }

        @Test
        void returnsPlainResult() {
            stub.nextJsonResponse = "{\"id\":\"folder789\"}";
            CallToolRequest request = new CallToolRequest("drive_files_create_folder",
                    Map.of("name", "My Folder"));
            CallToolResult result = invokeHandler("drive_files_create_folder", request);

            assertEquals(1, result.content().size());
        }

        @Test
        void returnsErrorWhenNameMissing() {
            CallToolRequest request = new CallToolRequest("drive_files_create_folder", Map.of());
            CallToolResult result = invokeHandler("drive_files_create_folder", request);

            assertTrue(result.isError());
        }
    }

    // ── drive_files_delete ──────────────────────────────────────

    @Nested
    class FilesDeleteTests {

        @Test
        void buildsCorrectCommand() {
            stub.nextJsonResponse = "";
            CallToolRequest request = new CallToolRequest("drive_files_delete",
                    Map.of("fileId", "del456"));
            invokeHandler("drive_files_delete", request);

            assertEquals("drive", stub.lastArgs.get(0));
            assertEquals("files", stub.lastArgs.get(1));
            assertEquals("delete", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));
            assertTrue(stub.lastArgs.get(4).contains("\"fileId\":\"del456\""));
        }

        @Test
        void returnsPlainResultWithDeleteConfirmation() {
            stub.nextJsonResponse = "";
            CallToolRequest request = new CallToolRequest("drive_files_delete",
                    Map.of("fileId", "del456"));
            CallToolResult result = invokeHandler("drive_files_delete", request);

            assertEquals(1, result.content().size());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("\"deleted\":true"));
            assertTrue(content.text().contains("\"fileId\":\"del456\""));
        }

        @Test
        void returnsErrorWhenFileIdMissing() {
            CallToolRequest request = new CallToolRequest("drive_files_delete", Map.of());
            CallToolResult result = invokeHandler("drive_files_delete", request);

            assertTrue(result.isError());
        }

        @Test
        void returnsErrorOnFailure() {
            stub.nextJsonResponse = null; // will throw
            CallToolRequest request = new CallToolRequest("drive_files_delete",
                    Map.of("fileId", "del456"));
            CallToolResult result = invokeHandler("drive_files_delete", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("drive_files_delete failed"));
        }
    }

    // ── Helper ──────────────────────────────────────────────────

    private CallToolResult invokeHandler(String toolName, CallToolRequest request) {
        return driveTools.tools().stream()
                .filter(t -> t.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName))
                .callHandler()
                .apply(null, request);
    }
}

/**
 * Stub GwsClient for testing tool handlers without subprocess execution.
 */
class StubGwsClient extends GwsClient {

    String nextJsonResponse;
    GwsResult nextExecuteResponse;
    List<String> lastArgs;

    StubGwsClient() {
        super("/bin/true", 5);
    }

    @Override
    public GwsResult execute(List<String> args) {
        this.lastArgs = args;
        if (nextExecuteResponse != null) {
            return nextExecuteResponse;
        }
        return new GwsResult(0, nextJsonResponse != null ? nextJsonResponse : "", "");
    }

    @Override
    public String executeJson(List<String> args) throws IOException {
        this.lastArgs = args;
        if (nextJsonResponse == null) {
            throw new IOException("No response configured");
        }
        return nextJsonResponse;
    }
}
