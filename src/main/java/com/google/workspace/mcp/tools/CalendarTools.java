package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.ContentSanitizer;
import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.util.*;

public class CalendarTools {

    private final GwsClient client;

    public CalendarTools(GwsClient client) {
        this.client = client;
    }

    public List<SyncToolSpecification> tools() {
        return List.of(eventsList(), eventsGet(), eventsCreate(), eventsDelete());
    }

    private SyncToolSpecification eventsList() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("calendar_events_list")
                        .title("List Calendar Events")
                        .description("List events from a Google Calendar with optional time range filter")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "calendarId", Map.of("type", "string", "description", "Calendar ID (default \"primary\")"),
                                "timeMin", Map.of("type", "string", "description", "Lower bound (inclusive) for event start time (RFC3339, e.g. 2025-01-01T00:00:00Z)"),
                                "timeMax", Map.of("type", "string", "description", "Upper bound (exclusive) for event start time (RFC3339, e.g. 2025-12-31T23:59:59Z)"),
                                "maxResults", Map.of("type", "integer", "description", "Max events to return (default 20)")
                        ), null, null, null, null))
                        .annotations(new ToolAnnotations("List Calendar Events", true, false, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("calendarId", calendarId(args));
                        params.put("maxResults", maxResults(args));
                        if (args.get("timeMin") instanceof String t && !t.isBlank()) {
                            params.put("timeMin", t);
                        }
                        if (args.get("timeMax") instanceof String t && !t.isBlank()) {
                            params.put("timeMax", t);
                        }
                        String json = client.executeJson(List.of(
                                "calendar", "events", "list",
                                "--params", GwsClient.paramsJson(params)));
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("calendar_events_list failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification eventsGet() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("calendar_events_get")
                        .title("Get Calendar Event")
                        .description("Get details of a specific calendar event by its ID")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "calendarId", Map.of("type", "string", "description", "Calendar ID (default \"primary\")"),
                                "eventId", Map.of("type", "string", "description", "The event ID")
                        ), List.of("eventId"), null, null, null))
                        .annotations(new ToolAnnotations("Get Calendar Event", true, false, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String eventId = requireString(args, "eventId");
                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("calendarId", calendarId(args));
                        params.put("eventId", eventId);
                        String json = client.executeJson(List.of(
                                "calendar", "events", "get",
                                "--params", GwsClient.paramsJson(params)));
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("calendar_events_get failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification eventsCreate() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("calendar_events_create")
                        .title("Create Calendar Event")
                        .description("Create a new event on a Google Calendar")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "calendarId", Map.of("type", "string", "description", "Calendar ID (default \"primary\")"),
                                "summary", Map.of("type", "string", "description", "Event title"),
                                "startTime", Map.of("type", "string", "description", "Start time (RFC3339, e.g. 2025-06-15T10:00:00Z)"),
                                "endTime", Map.of("type", "string", "description", "End time (RFC3339, e.g. 2025-06-15T11:00:00Z)"),
                                "attendees", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of attendee email addresses"),
                                "location", Map.of("type", "string", "description", "Event location"),
                                "description", Map.of("type", "string", "description", "Event description")
                        ), List.of("summary", "startTime", "endTime"), null, null, null))
                        .annotations(new ToolAnnotations("Create Calendar Event", false, false, false, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String summary = requireString(args, "summary");
                        String startTime = requireString(args, "startTime");
                        String endTime = requireString(args, "endTime");

                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("summary", summary);
                        body.put("start", Map.of("dateTime", startTime));
                        body.put("end", Map.of("dateTime", endTime));
                        if (args.get("attendees") instanceof List<?> attendees && !attendees.isEmpty()) {
                            List<Map<String, String>> attendeeList = new ArrayList<>();
                            for (Object a : attendees) {
                                if (a instanceof String email && !email.isBlank()) {
                                    attendeeList.add(Map.of("email", email));
                                }
                            }
                            if (!attendeeList.isEmpty()) {
                                body.put("attendees", attendeeList);
                            }
                        }
                        if (args.get("location") instanceof String loc && !loc.isBlank()) {
                            body.put("location", loc);
                        }
                        if (args.get("description") instanceof String desc && !desc.isBlank()) {
                            body.put("description", desc);
                        }

                        Map<String, Object> params = Map.of("calendarId", calendarId(args));
                        String json = client.executeJson(List.of(
                                "calendar", "events", "insert",
                                "--params", GwsClient.paramsJson(params),
                                "--json", GwsClient.bodyJson(body)));
                        return ContentSanitizer.plainResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("calendar_events_create failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification eventsDelete() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("calendar_events_delete")
                        .title("Delete Calendar Event")
                        .description("Delete an event from a Google Calendar")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "calendarId", Map.of("type", "string", "description", "Calendar ID (default \"primary\")"),
                                "eventId", Map.of("type", "string", "description", "The event ID to delete")
                        ), List.of("eventId"), null, null, null))
                        .annotations(new ToolAnnotations("Delete Calendar Event", false, true, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String eventId = requireString(args, "eventId");
                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("calendarId", calendarId(args));
                        params.put("eventId", eventId);
                        client.executeJson(List.of(
                                "calendar", "events", "delete",
                                "--params", GwsClient.paramsJson(params)));
                        return ContentSanitizer.plainResult("{\"deleted\":true,\"eventId\":\"" + eventId + "\"}");
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("calendar_events_delete failed: " + e.getMessage());
                    }
                })
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────

    static Map<String, Object> safeArgs(CallToolRequest request) {
        return request.arguments() != null ? request.arguments() : Map.of();
    }

    static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalArgumentException("'" + key + "' is required");
    }

    private static String calendarId(Map<String, Object> args) {
        if (args.get("calendarId") instanceof String id && !id.isBlank()) {
            return id;
        }
        return "primary";
    }

    private static int maxResults(Map<String, Object> args) {
        Object raw = args.get("maxResults");
        int size = 20;
        if (raw instanceof Number n) {
            size = n.intValue();
        } else if (raw instanceof String s) {
            try { size = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return Math.max(1, Math.min(100, size));
    }
}
