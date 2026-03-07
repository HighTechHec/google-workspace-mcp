# Google Workspace MCP Server

An MCP server that provides Google Workspace tools (Drive, Calendar, Sheets, Docs, Gmail, Chat, Admin) via the [gws CLI](https://github.com/nicholasgasior/gws).

## Prerequisites

- **Java 21+**
- **gws CLI** — install with `npm install -g @googleworkspace/cli`
- **Google Cloud project** with Workspace APIs enabled (the installer walks you through this)

## Quick Start

```bash
# Build
mvn clean package

# Install (sets up auth + registers with Claude Code)
java -jar target/google-workspace-mcp-1.0.0.jar --install

# Re-authenticate (if token expires)
java -jar target/google-workspace-mcp-1.0.0.jar --auth
```

The `--install` command will:
1. Verify `gws` is on your PATH
2. Run `gws auth setup` to configure your Google Cloud project
3. Run `gws auth login` to authenticate via browser
4. Register the MCP server with Claude Code

## Manual Registration

If automatic registration fails, add manually:

```bash
claude mcp add --scope user --transport stdio google-workspace -- \
  java -jar /path/to/google-workspace-mcp-1.0.0.jar
```

## Available Tools (28)

### Drive (6 tools)

| Tool | Description |
|------|-------------|
| `drive_files_list` | List files/folders with optional search query |
| `drive_files_get` | Get file metadata by ID |
| `drive_files_download` | Download file content |
| `drive_files_upload` | Upload a file |
| `drive_files_create_folder` | Create a folder |
| `drive_files_delete` | Delete a file/folder |

### Calendar (4 tools)

| Tool | Description |
|------|-------------|
| `calendar_events_list` | List events with optional time range |
| `calendar_events_get` | Get event details |
| `calendar_events_create` | Create a calendar event |
| `calendar_events_delete` | Delete a calendar event |

### Sheets (4 tools)

| Tool | Description |
|------|-------------|
| `sheets_list` | List spreadsheets |
| `sheets_values_get` | Read a cell range |
| `sheets_values_update` | Write to a cell range |
| `sheets_create` | Create a spreadsheet |

### Docs (3 tools)

| Tool | Description |
|------|-------------|
| `docs_get` | Get document content |
| `docs_create` | Create a document |
| `docs_list` | List documents |

### Gmail (5 tools)

| Tool | Description |
|------|-------------|
| `gmail_messages_list` | List/search messages |
| `gmail_messages_get` | Read a message |
| `gmail_labels_list` | List labels |
| `gmail_messages_send` | Send an email |
| `gmail_drafts_create` | Create a draft |

### Chat (3 tools)

| Tool | Description |
|------|-------------|
| `chat_spaces_list` | List Chat spaces |
| `chat_messages_list` | List messages in a space |
| `chat_messages_create` | Send a Chat message |

### Admin (3 tools)

| Tool | Description |
|------|-------------|
| `admin_users_list` | List directory users |
| `admin_users_get` | Get user details |
| `admin_groups_list` | List contact groups |

## Building from Source

```bash
git clone https://github.com/wrxck/google-workspace-mcp.git
cd google-workspace-mcp
mvn clean package
```

The shaded JAR is produced at `target/google-workspace-mcp-1.0.0.jar`.

## Architecture

```
Claude Code <-> (stdio) <-> google-workspace-mcp.jar
                              |
                         GwsClient (ProcessBuilder)
                              |
                         gws CLI binary
                              |
                         Google Workspace APIs
```

The server wraps the `gws` CLI as a subprocess. Each tool translates MCP arguments into `gws` command-line invocations and returns the JSON response. Untrusted content (email bodies, document content, etc.) is wrapped with random boundaries to mitigate prompt injection.

## License

Apache License 2.0
