package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.GwsClient;
import com.google.workspace.mcp.GwsClient.GwsResult;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
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

class CalendarToolsTest {

    private StubCalendarGwsClient stub;
    private CalendarTools calendarTools;

    @BeforeEach
    void setUp() {
        stub = new StubCalendarGwsClient();
        calendarTools = new CalendarTools(stub);
    }

    // ── tools() ─────────────────────────────────────────────────

    @Nested
    class ToolsTests {

        @Test
        void returnsExactlyFourTools() {
            List<SyncToolSpecification> tools = calendarTools.tools();
            assertEquals(4, tools.size());
        }

        @Test
        void toolNamesAreCorrect() {
            List<String> names = calendarTools.tools().stream()
                    .map(t -> t.tool().name())
                    .toList();
            assertEquals(List.of(
                    "calendar_events_list",
                    "calendar_events_get",
                    "calendar_events_create",
                    "calendar_events_delete"
            ), names);
        }
    }

    // ── safeArgs ────────────────────────────────────────────────

    @Nested
    class SafeArgsTests {

        @Test
        void returnsEmptyMapForNullArguments() {
            CallToolRequest request = new CallToolRequest("test", null);
            Map<String, Object> result = CalendarTools.safeArgs(request);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void returnsArgumentsMapWhenPresent() {
            Map<String, Object> args = Map.of("key", "value");
            CallToolRequest request = new CallToolRequest("test", args);
            assertSame(args, CalendarTools.safeArgs(request));
        }
    }

    // ── requireString ───────────────────────────────────────────

    @Nested
    class RequireStringTests {

        @Test
        void returnsValueForValidString() {
            assertEquals("hello", CalendarTools.requireString(Map.of("key", "hello"), "key"));
        }

        @Test
        void throwsForMissingKey() {
            assertThrows(IllegalArgumentException.class,
                    () -> CalendarTools.requireString(Map.of(), "key"));
        }

        @Test
        void throwsForBlankString() {
            assertThrows(IllegalArgumentException.class,
                    () -> CalendarTools.requireString(Map.of("key", "   "), "key"));
        }

        @Test
        void throwsForNonString() {
            assertThrows(IllegalArgumentException.class,
                    () -> CalendarTools.requireString(Map.of("key", 42), "key"));
        }
    }

    // ── calendar_events_list ────────────────────────────────────

    @Nested
    class EventsListTests {

        @Test
        void buildsCorrectCommandWithDefaults() {
            stub.nextJsonResponse = "{\"items\":[]}";
            CallToolRequest request = new CallToolRequest("calendar_events_list", Map.of());
            invokeHandler("calendar_events_list", request);

            assertNotNull(stub.lastArgs);
            assertEquals("calendar", stub.lastArgs.get(0));
            assertEquals("events", stub.lastArgs.get(1));
            assertEquals("list", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));
            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"primary\""));
            assertTrue(params.contains("\"maxResults\":20"));
        }

        @Test
        void defaultCalendarIdIsPrimary() {
            stub.nextJsonResponse = "{\"items\":[]}";
            CallToolRequest request = new CallToolRequest("calendar_events_list", Map.of());
            invokeHandler("calendar_events_list", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"primary\""));
        }

        @Test
        void usesCustomCalendarId() {
            stub.nextJsonResponse = "{\"items\":[]}";
            CallToolRequest request = new CallToolRequest("calendar_events_list",
                    Map.of("calendarId", "work@example.com"));
            invokeHandler("calendar_events_list", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"work@example.com\""));
        }

        @Test
        void includesTimeMinWhenProvided() {
            stub.nextJsonResponse = "{\"items\":[]}";
            CallToolRequest request = new CallToolRequest("calendar_events_list",
                    Map.of("timeMin", "2025-01-01T00:00:00Z"));
            invokeHandler("calendar_events_list", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"timeMin\":\"2025-01-01T00:00:00Z\""));
        }

        @Test
        void includesTimeMaxWhenProvided() {
            stub.nextJsonResponse = "{\"items\":[]}";
            CallToolRequest request = new CallToolRequest("calendar_events_list",
                    Map.of("timeMax", "2025-12-31T23:59:59Z"));
            invokeHandler("calendar_events_list", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"timeMax\":\"2025-12-31T23:59:59Z\""));
        }

        @Test
        void omitsTimeMinWhenNotProvided() {
            stub.nextJsonResponse = "{\"items\":[]}";
            CallToolRequest request = new CallToolRequest("calendar_events_list", Map.of());
            invokeHandler("calendar_events_list", request);

            String params = stub.lastArgs.get(4);
            assertFalse(params.contains("timeMin"));
        }

        @Test
        void omitsTimeMaxWhenNotProvided() {
            stub.nextJsonResponse = "{\"items\":[]}";
            CallToolRequest request = new CallToolRequest("calendar_events_list", Map.of());
            invokeHandler("calendar_events_list", request);

            String params = stub.lastArgs.get(4);
            assertFalse(params.contains("timeMax"));
        }

        @Test
        void returnsSanitizedResult() {
            stub.nextJsonResponse = "{\"items\":[{\"id\":\"evt1\"}]}";
            CallToolRequest request = new CallToolRequest("calendar_events_list", Map.of());
            CallToolResult result = invokeHandler("calendar_events_list", request);

            assertEquals(2, result.content().size());
            TextContent preamble = (TextContent) result.content().get(0);
            assertTrue(preamble.text().contains("SECURITY CONTEXT"));
        }

        @Test
        void returnsErrorOnFailure() {
            stub.nextJsonResponse = null;
            CallToolRequest request = new CallToolRequest("calendar_events_list", Map.of());
            CallToolResult result = invokeHandler("calendar_events_list", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("calendar_events_list failed"));
        }
    }

    // ── calendar_events_get ─────────────────────────────────────

    @Nested
    class EventsGetTests {

        @Test
        void buildsCorrectCommand() {
            stub.nextJsonResponse = "{\"id\":\"evt123\",\"summary\":\"Meeting\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_get",
                    Map.of("eventId", "evt123"));
            invokeHandler("calendar_events_get", request);

            assertEquals("calendar", stub.lastArgs.get(0));
            assertEquals("events", stub.lastArgs.get(1));
            assertEquals("get", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));
            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"primary\""));
            assertTrue(params.contains("\"eventId\":\"evt123\""));
        }

        @Test
        void defaultCalendarIdIsPrimary() {
            stub.nextJsonResponse = "{\"id\":\"evt123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_get",
                    Map.of("eventId", "evt123"));
            invokeHandler("calendar_events_get", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"primary\""));
        }

        @Test
        void usesCustomCalendarId() {
            stub.nextJsonResponse = "{\"id\":\"evt123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_get",
                    Map.of("eventId", "evt123", "calendarId", "work@example.com"));
            invokeHandler("calendar_events_get", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"work@example.com\""));
        }

        @Test
        void returnsSanitizedResult() {
            stub.nextJsonResponse = "{\"id\":\"evt123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_get",
                    Map.of("eventId", "evt123"));
            CallToolResult result = invokeHandler("calendar_events_get", request);

            assertEquals(2, result.content().size());
            TextContent preamble = (TextContent) result.content().get(0);
            assertTrue(preamble.text().contains("SECURITY CONTEXT"));
        }

        @Test
        void returnsErrorWhenEventIdMissing() {
            CallToolRequest request = new CallToolRequest("calendar_events_get", Map.of());
            CallToolResult result = invokeHandler("calendar_events_get", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("'eventId' is required"));
        }

        @Test
        void returnsErrorOnFailure() {
            stub.nextJsonResponse = null;
            CallToolRequest request = new CallToolRequest("calendar_events_get",
                    Map.of("eventId", "evt123"));
            CallToolResult result = invokeHandler("calendar_events_get", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("calendar_events_get failed"));
        }
    }

    // ── calendar_events_create ──────────────────────────────────

    @Nested
    class EventsCreateTests {

        @Test
        void buildsCorrectCommandWithRequiredArgs() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Team Standup",
                            "startTime", "2025-06-15T10:00:00Z",
                            "endTime", "2025-06-15T10:30:00Z"));
            invokeHandler("calendar_events_create", request);

            assertEquals("calendar", stub.lastArgs.get(0));
            assertEquals("events", stub.lastArgs.get(1));
            assertEquals("insert", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));
            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"primary\""));
            assertEquals("--json", stub.lastArgs.get(5));
            String body = stub.lastArgs.get(6);
            assertTrue(body.contains("\"summary\":\"Team Standup\""));
            assertTrue(body.contains("\"dateTime\":\"2025-06-15T10:00:00Z\""));
            assertTrue(body.contains("\"dateTime\":\"2025-06-15T10:30:00Z\""));
        }

        @Test
        void defaultCalendarIdIsPrimary() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Test", "startTime", "2025-06-15T10:00:00Z", "endTime", "2025-06-15T11:00:00Z"));
            invokeHandler("calendar_events_create", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"primary\""));
        }

        @Test
        void usesCustomCalendarId() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Test", "startTime", "2025-06-15T10:00:00Z",
                            "endTime", "2025-06-15T11:00:00Z", "calendarId", "work@example.com"));
            invokeHandler("calendar_events_create", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"work@example.com\""));
        }

        @Test
        void includesAttendeesWhenProvided() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Meeting",
                            "startTime", "2025-06-15T10:00:00Z",
                            "endTime", "2025-06-15T11:00:00Z",
                            "attendees", List.of("alice@example.com", "bob@example.com")));
            invokeHandler("calendar_events_create", request);

            String body = stub.lastArgs.get(6);
            assertTrue(body.contains("\"attendees\""));
            assertTrue(body.contains("alice@example.com"));
            assertTrue(body.contains("bob@example.com"));
        }

        @Test
        void omitsAttendeesWhenNotProvided() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Solo Work",
                            "startTime", "2025-06-15T10:00:00Z",
                            "endTime", "2025-06-15T11:00:00Z"));
            invokeHandler("calendar_events_create", request);

            String body = stub.lastArgs.get(6);
            assertFalse(body.contains("attendees"));
        }

        @Test
        void includesLocationWhenProvided() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Lunch",
                            "startTime", "2025-06-15T12:00:00Z",
                            "endTime", "2025-06-15T13:00:00Z",
                            "location", "Conference Room A"));
            invokeHandler("calendar_events_create", request);

            String body = stub.lastArgs.get(6);
            assertTrue(body.contains("\"location\":\"Conference Room A\""));
        }

        @Test
        void omitsLocationWhenNotProvided() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Test",
                            "startTime", "2025-06-15T10:00:00Z",
                            "endTime", "2025-06-15T11:00:00Z"));
            invokeHandler("calendar_events_create", request);

            String body = stub.lastArgs.get(6);
            assertFalse(body.contains("location"));
        }

        @Test
        void includesDescriptionWhenProvided() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Planning",
                            "startTime", "2025-06-15T14:00:00Z",
                            "endTime", "2025-06-15T15:00:00Z",
                            "description", "Quarterly planning session"));
            invokeHandler("calendar_events_create", request);

            String body = stub.lastArgs.get(6);
            assertTrue(body.contains("\"description\":\"Quarterly planning session\""));
        }

        @Test
        void omitsDescriptionWhenNotProvided() {
            stub.nextJsonResponse = "{\"id\":\"new123\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Test",
                            "startTime", "2025-06-15T10:00:00Z",
                            "endTime", "2025-06-15T11:00:00Z"));
            invokeHandler("calendar_events_create", request);

            String body = stub.lastArgs.get(6);
            assertFalse(body.contains("description"));
        }

        @Test
        void returnsPlainResult() {
            stub.nextJsonResponse = "{\"id\":\"new123\",\"summary\":\"Test\"}";
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Test",
                            "startTime", "2025-06-15T10:00:00Z",
                            "endTime", "2025-06-15T11:00:00Z"));
            CallToolResult result = invokeHandler("calendar_events_create", request);

            assertEquals(1, result.content().size());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("new123"));
        }

        @Test
        void returnsErrorWhenSummaryMissing() {
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("startTime", "2025-06-15T10:00:00Z",
                            "endTime", "2025-06-15T11:00:00Z"));
            CallToolResult result = invokeHandler("calendar_events_create", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("'summary' is required"));
        }

        @Test
        void returnsErrorWhenStartTimeMissing() {
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Test",
                            "endTime", "2025-06-15T11:00:00Z"));
            CallToolResult result = invokeHandler("calendar_events_create", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("'startTime' is required"));
        }

        @Test
        void returnsErrorWhenEndTimeMissing() {
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Test",
                            "startTime", "2025-06-15T10:00:00Z"));
            CallToolResult result = invokeHandler("calendar_events_create", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("'endTime' is required"));
        }

        @Test
        void returnsErrorOnFailure() {
            stub.nextJsonResponse = null;
            CallToolRequest request = new CallToolRequest("calendar_events_create",
                    Map.of("summary", "Test",
                            "startTime", "2025-06-15T10:00:00Z",
                            "endTime", "2025-06-15T11:00:00Z"));
            CallToolResult result = invokeHandler("calendar_events_create", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("calendar_events_create failed"));
        }
    }

    // ── calendar_events_delete ──────────────────────────────────

    @Nested
    class EventsDeleteTests {

        @Test
        void buildsCorrectCommand() {
            stub.nextJsonResponse = "";
            CallToolRequest request = new CallToolRequest("calendar_events_delete",
                    Map.of("eventId", "del456"));
            invokeHandler("calendar_events_delete", request);

            assertEquals("calendar", stub.lastArgs.get(0));
            assertEquals("events", stub.lastArgs.get(1));
            assertEquals("delete", stub.lastArgs.get(2));
            assertEquals("--params", stub.lastArgs.get(3));
            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"primary\""));
            assertTrue(params.contains("\"eventId\":\"del456\""));
        }

        @Test
        void defaultCalendarIdIsPrimary() {
            stub.nextJsonResponse = "";
            CallToolRequest request = new CallToolRequest("calendar_events_delete",
                    Map.of("eventId", "del456"));
            invokeHandler("calendar_events_delete", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"primary\""));
        }

        @Test
        void usesCustomCalendarId() {
            stub.nextJsonResponse = "";
            CallToolRequest request = new CallToolRequest("calendar_events_delete",
                    Map.of("eventId", "del456", "calendarId", "work@example.com"));
            invokeHandler("calendar_events_delete", request);

            String params = stub.lastArgs.get(4);
            assertTrue(params.contains("\"calendarId\":\"work@example.com\""));
        }

        @Test
        void returnsPlainResultWithDeleteConfirmation() {
            stub.nextJsonResponse = "";
            CallToolRequest request = new CallToolRequest("calendar_events_delete",
                    Map.of("eventId", "del456"));
            CallToolResult result = invokeHandler("calendar_events_delete", request);

            assertEquals(1, result.content().size());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("\"deleted\":true"));
            assertTrue(content.text().contains("\"eventId\":\"del456\""));
        }

        @Test
        void returnsErrorWhenEventIdMissing() {
            CallToolRequest request = new CallToolRequest("calendar_events_delete", Map.of());
            CallToolResult result = invokeHandler("calendar_events_delete", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("'eventId' is required"));
        }

        @Test
        void returnsErrorOnFailure() {
            stub.nextJsonResponse = null;
            CallToolRequest request = new CallToolRequest("calendar_events_delete",
                    Map.of("eventId", "del456"));
            CallToolResult result = invokeHandler("calendar_events_delete", request);

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("calendar_events_delete failed"));
        }
    }

    // ── Helper ──────────────────────────────────────────────────

    private CallToolResult invokeHandler(String toolName, CallToolRequest request) {
        return calendarTools.tools().stream()
                .filter(t -> t.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName))
                .callHandler()
                .apply(null, request);
    }
}

/**
 * Stub GwsClient for testing CalendarTools handlers without subprocess execution.
 */
class StubCalendarGwsClient extends GwsClient {

    String nextJsonResponse;
    GwsResult nextExecuteResponse;
    List<String> lastArgs;

    StubCalendarGwsClient() {
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
