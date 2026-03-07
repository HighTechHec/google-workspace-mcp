package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.ContentSanitizer;
import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin tools using the People API for directory user and group lookups.
 * Provides admin_users_list, admin_users_get, and admin_groups_list.
 */
public final class AdminTools {

    private static final Logger log = LoggerFactory.getLogger(AdminTools.class);
    private static final int DEFAULT_PAGE_SIZE = 50;

    private AdminTools() {}

    /** Return all admin tool specifications. */
    public static List<SyncToolSpecification> tools(GwsClient client) {
        return List.of(
                buildUsersListTool(client),
                buildUsersGetTool(client),
                buildGroupsListTool(client));
    }

    // ── admin_users_list ──────────────────────────────────────────

    private static SyncToolSpecification buildUsersListTool(GwsClient client) {
        var schema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "pageSize", Map.of("type", "integer", "description",
                                "Number of users to return (default 50)")
                ),
                Collections.emptyList(),
                false, null, null);

        var annotations = new McpSchema.ToolAnnotations(
                "List directory users", true, false, true, true, null);

        var tool = McpSchema.Tool.builder()
                .name("admin_users_list")
                .description("List directory users via the People API. Returns names and email addresses.")
                .inputSchema(schema)
                .annotations(annotations)
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        int pageSize = parsePageSize(args);

                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("sources", List.of("DIRECTORY_SOURCE_TYPE_DOMAIN_PROFILE"));
                        params.put("readMask", "names,emailAddresses");
                        params.put("pageSize", pageSize);

                        List<String> cmdArgs = List.of(
                                "people", "people", "listDirectoryPeople",
                                "--params", GwsClient.paramsJson(params));

                        String json = client.executeJson(cmdArgs);
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        log.error("admin_users_list failed", e);
                        return ContentSanitizer.errorResult("Error listing users: " + e.getMessage());
                    }
                })
                .build();
    }

    // ── admin_users_get ───────────────────────────────────────────

    private static SyncToolSpecification buildUsersGetTool(GwsClient client) {
        var schema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "resourceName", Map.of("type", "string", "description",
                                "Resource name of the person (e.g. 'people/123456')")
                ),
                List.of("resourceName"),
                false, null, null);

        var annotations = new McpSchema.ToolAnnotations(
                "Get user details", true, false, true, true, null);

        var tool = McpSchema.Tool.builder()
                .name("admin_users_get")
                .description("Get detailed user information via the People API. "
                        + "Returns names, email addresses, phone numbers, and organizations.")
                .inputSchema(schema)
                .annotations(annotations)
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String resourceName = requireString(args, "resourceName");

                        if (!resourceName.startsWith("people/")) {
                            resourceName = "people/" + resourceName;
                        }

                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("resourceName", resourceName);
                        params.put("personFields", "names,emailAddresses,phoneNumbers,organizations");

                        List<String> cmdArgs = List.of(
                                "people", "people", "get",
                                "--params", GwsClient.paramsJson(params));

                        String json = client.executeJson(cmdArgs);
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (IllegalArgumentException e) {
                        return ContentSanitizer.errorResult(e.getMessage());
                    } catch (Exception e) {
                        log.error("admin_users_get failed", e);
                        return ContentSanitizer.errorResult("Error getting user: " + e.getMessage());
                    }
                })
                .build();
    }

    // ── admin_groups_list ─────────────────────────────────────────

    private static SyncToolSpecification buildGroupsListTool(GwsClient client) {
        var schema = new McpSchema.JsonSchema(
                "object",
                Collections.emptyMap(),
                Collections.emptyList(),
                false, null, null);

        var annotations = new McpSchema.ToolAnnotations(
                "List contact groups", true, false, true, true, null);

        var tool = McpSchema.Tool.builder()
                .name("admin_groups_list")
                .description("List contact groups via the People API.")
                .inputSchema(schema)
                .annotations(annotations)
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        List<String> cmdArgs = List.of(
                                "people", "contactGroups", "list");

                        String json = client.executeJson(cmdArgs);
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        log.error("admin_groups_list failed", e);
                        return ContentSanitizer.errorResult("Error listing groups: " + e.getMessage());
                    }
                })
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    static Map<String, Object> safeArgs(McpSchema.CallToolRequest request) {
        return request.arguments() != null ? request.arguments() : Map.of();
    }

    static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException("'" + key + "' is required");
        }
        return value.toString();
    }

    static int parsePageSize(Map<String, Object> args) {
        Object raw = args.get("pageSize");
        if (raw instanceof Number n) {
            return n.intValue();
        } else if (raw instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return DEFAULT_PAGE_SIZE;
            }
        }
        return DEFAULT_PAGE_SIZE;
    }
}
