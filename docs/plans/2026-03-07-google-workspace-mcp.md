# Google Workspace MCP Server — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Java 21 MCP server wrapping the `gws` CLI (`@googleworkspace/cli`) to expose Google Workspace APIs as MCP tools.

**Architecture:** Subprocess wrapper around the `gws` Rust CLI binary. GwsClient handles process execution, each service has a tools class that builds gws commands and returns results. ContentSanitizer wraps untrusted content. GwsAuth delegates auth to `gws auth` and registers with Claude Code.

**Tech Stack:** Java 21, Maven, MCP SDK 1.0.0 (mcp-core + mcp-json-jackson2), Jackson 2, JUnit 5, Logback

---

## Dependency Graph

```
Task 1 (Scaffold) ──→ Tasks 2,3,4 (parallel: GwsClient, ContentSanitizer, GwsAuth)
                              │
                              ▼
                      Tasks 5-11 (parallel: 7 tool classes)
                              │
                              ▼
                      Task 12 (Server assembly)
                              │
                              ▼
                      Tasks 13-14 (README, LICENSE, git)
```

---

### Task 1: Project Scaffold

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/logback.xml`
- Create: `LICENSE`
- Create: `.gitignore`

**Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.github.wrxck</groupId>
    <artifactId>google-workspace-mcp</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>Google Workspace MCP Server</name>
    <description>MCP server providing Google Workspace tools (Drive, Calendar, Sheets, Docs, Gmail, Chat, Admin) via the gws CLI</description>
    <url>https://github.com/wrxck/google-workspace-mcp</url>

    <licenses>
        <license>
            <name>Apache License 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
    </licenses>

    <scm>
        <connection>scm:git:https://github.com/wrxck/google-workspace-mcp.git</connection>
        <developerConnection>scm:git:https://github.com/wrxck/google-workspace-mcp.git</developerConnection>
        <url>https://github.com/wrxck/google-workspace-mcp</url>
    </scm>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <mcp-sdk.version>1.0.0</mcp-sdk.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.modelcontextprotocol.sdk</groupId>
                <artifactId>mcp-bom</artifactId>
                <version>${mcp-sdk.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-json-jackson2</artifactId>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.5.16</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.google.workspace.mcp.GoogleWorkspaceMcpServer</mainClass>
                                </transformer>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                            </transformers>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 2: Create logback.xml**

```xml
<configuration>
    <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
        <target>System.err</target>
        <encoder>
            <pattern>%d{HH:mm:ss} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDERR"/>
    </root>
</configuration>
```

**Step 3: Create LICENSE** — Apache 2.0 (inheriting from gws CLI)

**Step 4: Create .gitignore** — Standard Java/Maven ignores

**Step 5: Git init and feature branch**

```bash
cd /home/matt/mcp/google-workspace
git init && git checkout -b develop
git checkout -b feat/initial develop
```

**Step 6: Initial commit**

---

### Task 2: GwsClient — Subprocess Execution

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/GwsClient.java`
- Create: `src/test/java/com/google/workspace/mcp/GwsClientTest.java`

**GwsClient** wraps `gws` CLI as subprocess. Key features:
- `execute(List<String> args)` → `GwsResult(exitCode, stdout, stderr)`
- `executeJson(List<String> args)` → String (JSON output)
- Concurrent stdout/stderr reading to prevent deadlock
- Configurable timeout (default 30s)
- `isAvailable()` check
- Static helpers: `paramsJson(Map)`, `bodyJson(Map)` for building --params/--json args

**GwsResult** is a record: `record GwsResult(int exitCode, String stdout, String stderr)`

**Tests:**
- Command list construction
- Successful execution returns stdout
- Non-zero exit throws IOException
- Timeout handling (destroys process)
- `paramsJson` serializes map to JSON
- `bodyJson` serializes map to JSON
- `isAvailable()` returns true/false
- Empty stdout/stderr handled

---

### Task 3: ContentSanitizer

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/ContentSanitizer.java`
- Create: `src/test/java/com/google/workspace/mcp/ContentSanitizerTest.java`

Same pattern as gmail-mcp ContentSanitizer but simplified for CLI wrapper:
- `generateBoundary()` — random 16-hex boundary
- `wrapContent(String json, String boundary)` — wraps JSON in boundaries
- `buildSecurityContext(String boundary)` — security preamble
- `truncate(String content, int maxLength)` — truncation with [TRUNCATED] marker
- `sanitizedResult(String json)` — convenience: generate boundary, build preamble + wrapped content as CallToolResult
- `plainResult(String json)` — no boundaries, just JSON as CallToolResult
- `errorResult(String message)` — error CallToolResult
- Constants: `MAX_CONTENT_LENGTH = 100_000`

**Tests:**
- Boundary generation (prefix, length, hex chars, uniqueness)
- Content wrapping with boundaries
- Security context mentions boundary and warnings
- Truncation (null, short, exact, long)
- sanitizedResult produces 2 text contents (preamble + wrapped)
- plainResult produces 1 text content
- errorResult sets isError flag

---

### Task 4: GwsAuth — Authentication & Installation

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/GwsAuth.java`
- Create: `src/test/java/com/google/workspace/mcp/GwsAuthTest.java`

Handles `--install` and `--auth` flags:

**--install flow:**
1. Check gws on PATH → print install instructions if missing
2. Run `gws auth setup` (interactive, creates GCP project + enables APIs)
3. Run `gws auth login --scopes drive,gmail,calendar,chat,admin-directory,sheets,docs` (browser OAuth)
4. Register with Claude Code: `claude mcp add --scope user --transport stdio google-workspace -- java -jar <jarPath>`
5. Print success/manual instructions

**--auth flow:**
1. Run `gws auth login --scopes drive,gmail,calendar,chat,admin-directory,sheets,docs`

**Key methods:**
- `install(String claudeBinary)` — full install flow
- `authenticate()` — just re-auth
- `findGwsBinary()` — check PATH for gws
- `findClaudeBinary()` — check PATH for claude
- `isGwsAuthenticated()` — run `gws auth status` to check
- `isAlreadyRegistered(String claudeBinary)` — check claude mcp list
- `resolveJarPath()` — detect JAR location
- `printManualRegistration()` — fallback instructions

**Tests:**
- resolveJarPath returns absolute path
- findGwsBinary/findClaudeBinary search candidates
- isAlreadyRegistered parses output correctly

---

### Task 5: DriveTools (6 tools)

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/tools/DriveTools.java`
- Create: `src/test/java/com/google/workspace/mcp/tools/DriveToolsTest.java`

| Tool | gws Command | Params |
|------|-------------|--------|
| `drive_files_list` | `gws drive files list --params '{"pageSize":N,"q":"..."}'` | query (string), pageSize (int, default 20) |
| `drive_files_get` | `gws drive files get --params '{"fileId":"ID"}'` | fileId (string, required) |
| `drive_files_download` | `gws drive files get --params '{"fileId":"ID","alt":"media"}' -o <path>` | fileId (string, required) |
| `drive_files_upload` | `gws drive files create --json '{"name":"...","parents":["..."]}' --upload <path>` | filePath (string, required), name (string), parentId (string) |
| `drive_files_create_folder` | `gws drive files create --json '{"name":"...","mimeType":"application/vnd.google-apps.folder","parents":["..."]}'` | name (string, required), parentId (string) |
| `drive_files_delete` | `gws drive files delete --params '{"fileId":"ID"}'` | fileId (string, required) |

All tools take GwsClient and ContentSanitizer in constructor. Read-only tools use sanitizedResult, write tools use plainResult.

**Annotations:** drive_files_list/get/download are readOnly. drive_files_delete is destructive. All are openWorld.

**Tests per tool:** Verify correct gws command args are built from MCP arguments. Verify response formatting. Verify required arg validation.

---

### Task 6: CalendarTools (4 tools)

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/tools/CalendarTools.java`
- Create: `src/test/java/com/google/workspace/mcp/tools/CalendarToolsTest.java`

| Tool | gws Command | Params |
|------|-------------|--------|
| `calendar_events_list` | `gws calendar events list --params '{"calendarId":"primary","timeMin":"...","timeMax":"...","maxResults":N}'` | calendarId (string, default "primary"), timeMin (string), timeMax (string), maxResults (int, default 20) |
| `calendar_events_get` | `gws calendar events get --params '{"calendarId":"primary","eventId":"ID"}'` | calendarId (string, default "primary"), eventId (string, required) |
| `calendar_events_create` | `gws calendar events insert --params '{"calendarId":"primary"}' --json '{"summary":"...","start":{"dateTime":"..."},"end":{"dateTime":"..."},"attendees":[...]}'` | calendarId (default "primary"), summary (required), startTime (required), endTime (required), attendees (string[], optional), location (string), description (string) |
| `calendar_events_delete` | `gws calendar events delete --params '{"calendarId":"primary","eventId":"ID"}'` | calendarId (default "primary"), eventId (required) |

---

### Task 7: SheetsTools (4 tools)

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/tools/SheetsTools.java`
- Create: `src/test/java/com/google/workspace/mcp/tools/SheetsToolsTest.java`

| Tool | gws Command | Params |
|------|-------------|--------|
| `sheets_list` | `gws drive files list --params '{"q":"mimeType='"'"'application/vnd.google-apps.spreadsheet'"'"'","pageSize":N}'` | pageSize (int, default 20) |
| `sheets_values_get` | `gws sheets spreadsheets values get --params '{"spreadsheetId":"ID","range":"..."}'` | spreadsheetId (required), range (required) |
| `sheets_values_update` | `gws sheets spreadsheets values update --params '{"spreadsheetId":"ID","range":"...","valueInputOption":"USER_ENTERED"}' --json '{"values":[...]}'` | spreadsheetId (required), range (required), values (array, required) |
| `sheets_create` | `gws sheets spreadsheets create --json '{"properties":{"title":"..."}}'` | title (required) |

---

### Task 8: DocsTools (3 tools)

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/tools/DocsTools.java`
- Create: `src/test/java/com/google/workspace/mcp/tools/DocsToolsTest.java`

| Tool | gws Command | Params |
|------|-------------|--------|
| `docs_get` | `gws docs documents get --params '{"documentId":"ID"}'` | documentId (required) |
| `docs_create` | `gws docs documents create --json '{"title":"..."}'` | title (required) |
| `docs_list` | `gws drive files list --params '{"q":"mimeType='"'"'application/vnd.google-apps.document'"'"'","pageSize":N}'` | pageSize (int, default 20) |

---

### Task 9: GmailTools (5 tools)

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/tools/GmailTools.java`
- Create: `src/test/java/com/google/workspace/mcp/tools/GmailToolsTest.java`

| Tool | gws Command | Params |
|------|-------------|--------|
| `gmail_messages_list` | `gws gmail users messages list --params '{"userId":"me","maxResults":N,"q":"..."}'` | query (string), maxResults (int, default 10) |
| `gmail_messages_get` | `gws gmail users messages get --params '{"userId":"me","id":"ID","format":"full"}'` | messageId (required) |
| `gmail_labels_list` | `gws gmail users labels list --params '{"userId":"me"}'` | none |
| `gmail_messages_send` | `gws gmail +send --to <addr> --subject <subj> --body <body>` | to (required), subject (required), body (required), cc (string), bcc (string) |
| `gmail_drafts_create` | Build RFC 2822 + base64, then `gws gmail users drafts create --params '{"userId":"me"}' --json '{"message":{"raw":"..."}}'` | to (required), subject (required), body (required) |

**Note:** gmail_drafts_create needs RFC 2822 message construction:
```java
String rfc2822 = "To: " + to + "\r\nSubject: " + subject
    + "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n" + body;
String encoded = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(rfc2822.getBytes(StandardCharsets.UTF_8));
```

---

### Task 10: ChatTools (3 tools)

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/tools/ChatTools.java`
- Create: `src/test/java/com/google/workspace/mcp/tools/ChatToolsTest.java`

| Tool | gws Command | Params |
|------|-------------|--------|
| `chat_spaces_list` | `gws chat spaces list` | none |
| `chat_messages_list` | `gws chat spaces messages list --params '{"parent":"spaces/SPACE_ID"}'` | spaceId (required) |
| `chat_messages_create` | `gws chat spaces messages create --params '{"parent":"spaces/SPACE_ID"}' --json '{"text":"..."}'` | spaceId (required), text (required) |

---

### Task 11: AdminTools (3 tools)

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/tools/AdminTools.java`
- Create: `src/test/java/com/google/workspace/mcp/tools/AdminToolsTest.java`

Uses People API since gws doesn't expose admin-directory directly:

| Tool | gws Command | Params |
|------|-------------|--------|
| `admin_users_list` | `gws people people listDirectoryPeople --params '{"sources":["DIRECTORY_SOURCE_TYPE_DOMAIN_PROFILE"],"readMask":"names,emailAddresses","pageSize":N}'` | pageSize (int, default 50) |
| `admin_users_get` | `gws people people get --params '{"resourceName":"people/ID","personFields":"names,emailAddresses,phoneNumbers,organizations"}'` | resourceName (required) |
| `admin_groups_list` | `gws people contactGroups list` | none |

---

### Task 12: GoogleWorkspaceMcpServer — Entry Point

**Files:**
- Create: `src/main/java/com/google/workspace/mcp/GoogleWorkspaceMcpServer.java`
- Create: `src/test/java/com/google/workspace/mcp/GoogleWorkspaceMcpServerTest.java`

**main():**
- `--install [--claude-binary <path>]` → GwsAuth.install()
- `--auth` → GwsAuth.authenticate()
- default → startServer()

**startServer():**
1. Check gws available, exit with error if not
2. Create GwsClient + ContentSanitizer
3. Create all 7 tool classes
4. Collect all SyncToolSpecifications into one list
5. Build McpSyncServer with stdio transport, register all tools
6. Add shutdown hook

```java
var transport = new StdioServerTransportProvider(
    new JacksonMcpJsonMapper(new ObjectMapper()));

List<SyncToolSpecification> allTools = new ArrayList<>();
allTools.addAll(new DriveTools(client, sanitizer).tools());
allTools.addAll(new CalendarTools(client, sanitizer).tools());
// ... etc

McpSyncServer server = McpServer.sync(transport)
    .serverInfo("google-workspace", "1.0.0")
    .capabilities(ServerCapabilities.builder().tools(true).build())
    .tools(allTools)
    .build();
```

**Tests:**
- Argument parsing for --install, --auth, default
- All 28 tools registered (count check)

---

### Task 13: README

**File:** Create `README.md`

Cover: what it does, prerequisites (gws CLI + Java 21), installation (one-command), authentication, available tools (grouped by service), building from source, license.

---

### Task 14: Build, Test, Git Push

1. `mvn clean package` — verify build
2. `mvn test` — all tests pass
3. Git: create private repo, push, create develop+main branches
