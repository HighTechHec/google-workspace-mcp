package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.ContentSanitizer;
import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.nio.file.Path;
import java.util.*;

public class DriveTools {

    private final GwsClient client;

    public DriveTools(GwsClient client) {
        this.client = client;
    }

    public List<SyncToolSpecification> tools() {
        return List.of(filesList(), filesGet(), filesDownload(), filesUpload(), filesCreateFolder(), filesDelete());
    }

    private SyncToolSpecification filesList() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("drive_files_list")
                        .title("List Drive Files")
                        .description("List files and folders in Google Drive with optional search query")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "query", Map.of("type", "string", "description", "Search query (Google Drive query syntax, e.g. \"name contains 'report'\")"),
                                "pageSize", Map.of("type", "integer", "description", "Max results to return (1-100, default 20)")
                        ), null, null, null, null))
                        .annotations(new ToolAnnotations("List Drive Files", true, false, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("pageSize", parsePageSize(args.get("pageSize")));
                        if (args.get("query") instanceof String q && !q.isBlank()) {
                            params.put("q", q);
                        }
                        String json = client.executeJson(List.of(
                                "drive", "files", "list",
                                "--params", GwsClient.paramsJson(params)));
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("drive_files_list failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification filesGet() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("drive_files_get")
                        .title("Get Drive File")
                        .description("Get metadata for a file or folder by its ID")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "fileId", Map.of("type", "string", "description", "The file ID")
                        ), List.of("fileId"), null, null, null))
                        .annotations(new ToolAnnotations("Get Drive File", true, false, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String fileId = requireString(args, "fileId");
                        String json = client.executeJson(List.of(
                                "drive", "files", "get",
                                "--params", GwsClient.paramsJson(Map.of("fileId", fileId))));
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("drive_files_get failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification filesDownload() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("drive_files_download")
                        .title("Download Drive File")
                        .description("Download a file's content from Google Drive. Saves to ~/.google-workspace-mcp/downloads/ and returns the file path.")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "fileId", Map.of("type", "string", "description", "The file ID to download")
                        ), List.of("fileId"), null, null, null))
                        .annotations(new ToolAnnotations("Download Drive File", true, false, false, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String fileId = requireString(args, "fileId");

                        Path downloadDir = Path.of(System.getProperty("user.home"), ".google-workspace-mcp", "downloads");
                        downloadDir.toFile().mkdirs();
                        Path outputFile = downloadDir.resolve(fileId);

                        client.execute(List.of(
                                "drive", "files", "get",
                                "--params", GwsClient.paramsJson(Map.of("fileId", fileId, "alt", "media")),
                                "-o", outputFile.toString()));
                        return ContentSanitizer.plainResult("{\"savedTo\":\"" + outputFile + "\",\"fileId\":\"" + fileId + "\"}");
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("drive_files_download failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification filesUpload() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("drive_files_upload")
                        .title("Upload to Drive")
                        .description("Upload a file to Google Drive")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "filePath", Map.of("type", "string", "description", "Local file path to upload"),
                                "name", Map.of("type", "string", "description", "Name for the file in Drive (defaults to local filename)"),
                                "parentId", Map.of("type", "string", "description", "Parent folder ID (optional)")
                        ), List.of("filePath"), null, null, null))
                        .annotations(new ToolAnnotations("Upload to Drive", false, false, false, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String filePath = requireString(args, "filePath");
                        Map<String, Object> body = new LinkedHashMap<>();
                        if (args.get("name") instanceof String name && !name.isBlank()) {
                            body.put("name", name);
                        }
                        if (args.get("parentId") instanceof String parentId && !parentId.isBlank()) {
                            body.put("parents", List.of(parentId));
                        }
                        List<String> cmd = new ArrayList<>(List.of("drive", "files", "create"));
                        if (!body.isEmpty()) {
                            cmd.add("--json");
                            cmd.add(GwsClient.bodyJson(body));
                        }
                        cmd.add("--upload");
                        cmd.add(filePath);
                        String json = client.executeJson(cmd);
                        return ContentSanitizer.plainResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("drive_files_upload failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification filesCreateFolder() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("drive_files_create_folder")
                        .title("Create Drive Folder")
                        .description("Create a new folder in Google Drive")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "name", Map.of("type", "string", "description", "Folder name"),
                                "parentId", Map.of("type", "string", "description", "Parent folder ID (optional)")
                        ), List.of("name"), null, null, null))
                        .annotations(new ToolAnnotations("Create Drive Folder", false, false, false, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String name = requireString(args, "name");
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("name", name);
                        body.put("mimeType", "application/vnd.google-apps.folder");
                        if (args.get("parentId") instanceof String parentId && !parentId.isBlank()) {
                            body.put("parents", List.of(parentId));
                        }
                        String json = client.executeJson(List.of(
                                "drive", "files", "create",
                                "--json", GwsClient.bodyJson(body)));
                        return ContentSanitizer.plainResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("drive_files_create_folder failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification filesDelete() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("drive_files_delete")
                        .title("Delete Drive File")
                        .description("Delete a file or folder from Google Drive")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "fileId", Map.of("type", "string", "description", "The file ID to delete")
                        ), List.of("fileId"), null, null, null))
                        .annotations(new ToolAnnotations("Delete Drive File", false, true, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String fileId = requireString(args, "fileId");
                        client.executeJson(List.of(
                                "drive", "files", "delete",
                                "--params", GwsClient.paramsJson(Map.of("fileId", fileId))));
                        return ContentSanitizer.plainResult("{\"deleted\":true,\"fileId\":\"" + fileId + "\"}");
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("drive_files_delete failed: " + e.getMessage());
                    }
                })
                .build();
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

    static int parsePageSize(Object raw) {
        int size = 20;
        if (raw instanceof Number n) {
            size = n.intValue();
        } else if (raw instanceof String s) {
            try { size = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return Math.max(1, Math.min(100, size));
    }
}
