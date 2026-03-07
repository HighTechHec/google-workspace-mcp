package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.ContentSanitizer;
import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.util.List;
import java.util.Map;

public class ChatTools {

    private final GwsClient client;

    public ChatTools(GwsClient client) {
        this.client = client;
    }

    public List<SyncToolSpecification> tools() {
        return List.of(spacesList(), messagesList(), messagesCreate());
    }

    private SyncToolSpecification spacesList() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("chat_spaces_list")
                        .title("List Chat Spaces")
                        .description("List Google Chat spaces the authenticated user is a member of")
                        .inputSchema(new JsonSchema("object", Map.of(), null, null, null, null))
                        .annotations(new ToolAnnotations("List Chat Spaces", true, false, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        String json = client.executeJson(List.of("chat", "spaces", "list"));
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("chat_spaces_list failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification messagesList() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("chat_messages_list")
                        .title("List Chat Messages")
                        .description("List messages in a Google Chat space")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "spaceId", Map.of("type", "string", "description", "The space ID (e.g. 'AAAA1234' or 'spaces/AAAA1234')")
                        ), List.of("spaceId"), null, null, null))
                        .annotations(new ToolAnnotations("List Chat Messages", true, false, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String spaceId = requireString(args, "spaceId");
                        String parent = ensureSpacesPrefix(spaceId);
                        String json = client.executeJson(List.of(
                                "chat", "spaces", "messages", "list",
                                "--params", GwsClient.paramsJson(Map.of("parent", parent))));
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("chat_messages_list failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification messagesCreate() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("chat_messages_create")
                        .title("Send Chat Message")
                        .description("Send a message to a Google Chat space")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "spaceId", Map.of("type", "string", "description", "The space ID (e.g. 'AAAA1234' or 'spaces/AAAA1234')"),
                                "text", Map.of("type", "string", "description", "The message text to send")
                        ), List.of("spaceId", "text"), null, null, null))
                        .annotations(new ToolAnnotations("Send Chat Message", false, false, false, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String spaceId = requireString(args, "spaceId");
                        String text = requireString(args, "text");
                        String parent = ensureSpacesPrefix(spaceId);
                        String json = client.executeJson(List.of(
                                "chat", "spaces", "messages", "create",
                                "--params", GwsClient.paramsJson(Map.of("parent", parent)),
                                "--json", GwsClient.bodyJson(Map.of("text", text))));
                        return ContentSanitizer.plainResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("chat_messages_create failed: " + e.getMessage());
                    }
                })
                .build();
    }

    static String ensureSpacesPrefix(String spaceId) {
        return spaceId.startsWith("spaces/") ? spaceId : "spaces/" + spaceId;
    }

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
}
