# Google Workspace MCP Server - Design

## Overview

Java 21 MCP server wrapping the `gws` CLI binary (`@googleworkspace/cli`) as a subprocess. Exposes Google Workspace APIs (Drive, Calendar, Sheets, Docs, Gmail, Chat, Admin) as MCP tools via stdio transport.

## Architecture

```
Claude Code <-> (stdio) <-> google-workspace-mcp.jar
                                |
                           GwsClient (ProcessBuilder)
                                |
                           gws CLI binary (Rust/npm)
                                |
                           Google Workspace APIs
```

- **Transport:** stdio (JSON-RPC)
- **MCP SDK:** 1.0.0 GA
- **Java:** 21
- **Build:** Maven, shaded JAR
- **License:** Apache 2.0 (inheriting from gws CLI)

## Tool Inventory (28 tools)

### Drive (6 tools)

| Tool | Description | Read-only | Destructive |
|------|-------------|-----------|-------------|
| `drive_files_list` | List files/folders with optional query | yes | no |
| `drive_files_get` | Get file metadata by ID | yes | no |
| `drive_files_download` | Download file content | yes | no |
| `drive_files_upload` | Upload a file | no | no |
| `drive_files_create_folder` | Create a folder | no | no |
| `drive_files_delete` | Delete a file/folder | no | yes |

### Calendar (4 tools)

| Tool | Description | Read-only | Destructive |
|------|-------------|-----------|-------------|
| `calendar_events_list` | List events with optional time range | yes | no |
| `calendar_events_get` | Get event details | yes | no |
| `calendar_events_create` | Create a calendar event | no | no |
| `calendar_events_delete` | Delete a calendar event | no | yes |

### Sheets (4 tools)

| Tool | Description | Read-only | Destructive |
|------|-------------|-----------|-------------|
| `sheets_list` | List spreadsheets | yes | no |
| `sheets_values_get` | Read a cell range | yes | no |
| `sheets_values_update` | Write to a cell range | no | no |
| `sheets_create` | Create a spreadsheet | no | no |

### Docs (3 tools)

| Tool | Description | Read-only | Destructive |
|------|-------------|-----------|-------------|
| `docs_get` | Get document content | yes | no |
| `docs_create` | Create a document | no | no |
| `docs_list` | List documents (Drive query) | yes | no |

### Gmail (5 tools)

| Tool | Description | Read-only | Destructive |
|------|-------------|-----------|-------------|
| `gmail_messages_list` | List/search messages | yes | no |
| `gmail_messages_get` | Read a message | yes | no |
| `gmail_labels_list` | List labels | yes | no |
| `gmail_messages_send` | Send an email | no | no |
| `gmail_drafts_create` | Create a draft | no | no |

### Chat (3 tools)

| Tool | Description | Read-only | Destructive |
|------|-------------|-----------|-------------|
| `chat_spaces_list` | List Chat spaces | yes | no |
| `chat_messages_list` | List messages in a space | yes | no |
| `chat_messages_create` | Send a Chat message | no | no |

### Admin (3 tools)

| Tool | Description | Read-only | Destructive |
|------|-------------|-----------|-------------|
| `admin_users_list` | List directory users | yes | no |
| `admin_users_get` | Get user details | yes | no |
| `admin_groups_list` | List groups | yes | no |

## Class Structure

```
com.google.workspace.mcp/
  GoogleWorkspaceMcpServer.java  - Entry point, server setup, tool registration
  GwsClient.java                 - Subprocess execution, JSON parsing, error handling
  GwsAuth.java                   - Auth delegation (gws auth setup/login)
  ContentSanitizer.java          - Prompt injection mitigations
  tools/
    DriveTools.java              - 6 Drive tools
    CalendarTools.java           - 4 Calendar tools
    SheetsTools.java             - 4 Sheets tools
    DocsTools.java               - 3 Docs tools
    GmailTools.java              - 5 Gmail tools
    ChatTools.java               - 3 Chat tools
    AdminTools.java              - 3 Admin tools
```

## Authentication

Delegates to `gws auth`. The server provides a `--auth` flag:
1. Runs `gws auth setup` (configures OAuth client)
2. Runs `gws auth login` (browser-based OAuth flow)

Subsequent tool calls assume auth is configured. GwsClient checks for valid auth before execution and returns clear errors if not configured.

## Security

- **ContentSanitizer:** Random boundary wrapping for untrusted content (email bodies, document content, file names, chat messages)
- **Tool annotations:** readOnlyHint, destructiveHint set accurately per tool
- **Input validation:** IDs, ranges, queries validated before subprocess execution
- **Command injection prevention:** Arguments passed as list to ProcessBuilder, never shell-interpolated

## Error Handling

- GwsClient captures stderr and exit code from gws process
- Non-zero exit codes mapped to MCP error responses
- Timeout handling for long-running operations (configurable, default 30s)
- Missing gws binary detected at startup with clear error message

## Testing Strategy

- **GwsClientTest:** Command construction, JSON parsing, error handling, timeout — mocks ProcessBuilder
- **ContentSanitizerTest:** Boundary generation, field wrapping, truncation
- **Tool tests per service:** Argument validation, gws command mapping, response formatting
- **AuthTest:** Auth flow, missing credentials detection
- **ServerTest:** Tool registration, argument parsing
