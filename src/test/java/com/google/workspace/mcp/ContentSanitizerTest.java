package com.google.workspace.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentSanitizerTest {

    private static final String BOUNDARY_PREFIX = "----UNTRUSTED_CONTENT_";

    // --- generateBoundary tests ---

    @Test
    void generateBoundary_startsWithPrefix() {
        String boundary = ContentSanitizer.generateBoundary();
        assertTrue(boundary.startsWith(BOUNDARY_PREFIX),
                "Boundary should start with '" + BOUNDARY_PREFIX + "' but was: " + boundary);
    }

    @Test
    void generateBoundary_hasCorrectLength() {
        String boundary = ContentSanitizer.generateBoundary();
        int expectedLength = BOUNDARY_PREFIX.length() + 16; // 8 bytes = 16 hex chars
        assertEquals(expectedLength, boundary.length(),
                "Boundary should be prefix + 16 hex chars");
    }

    @Test
    void generateBoundary_containsOnlyHexAfterPrefix() {
        String boundary = ContentSanitizer.generateBoundary();
        String hexPart = boundary.substring(BOUNDARY_PREFIX.length());
        assertTrue(hexPart.matches("[0-9a-f]+"),
                "Characters after prefix should be lowercase hex, but was: " + hexPart);
    }

    @Test
    void generateBoundary_producesDifferentValues() {
        String boundary1 = ContentSanitizer.generateBoundary();
        String boundary2 = ContentSanitizer.generateBoundary();
        assertNotEquals(boundary1, boundary2,
                "Two calls should produce different boundaries");
    }

    // --- truncate tests ---

    @Test
    void truncate_returnsNullForNullInput() {
        assertNull(ContentSanitizer.truncate(null, 100));
    }

    @Test
    void truncate_returnsOriginalForContentUnderMaxLength() {
        String content = "short content";
        assertSame(content, ContentSanitizer.truncate(content, 100));
    }

    @Test
    void truncate_returnsOriginalForContentExactlyAtMaxLength() {
        String content = "12345";
        assertSame(content, ContentSanitizer.truncate(content, 5));
    }

    @Test
    void truncate_truncatesAndAppendsMarkerForContentOverMaxLength() {
        String content = "abcdefghij";
        String result = ContentSanitizer.truncate(content, 5);
        assertEquals("abcde\n[TRUNCATED]", result);
    }

    @Test
    void truncate_customMaxLengthWorks() {
        String content = "hello world";
        String result = ContentSanitizer.truncate(content, 3);
        assertEquals("hel\n[TRUNCATED]", result);
    }

    // --- wrapContent tests ---

    @Test
    void wrapContent_wrapsContentWithBoundaryOnBothSides() {
        String boundary = "----TEST_BOUNDARY----";
        String json = "{\"key\":\"value\"}";
        String result = ContentSanitizer.wrapContent(json, boundary);
        assertEquals(boundary + "\n" + json + "\n" + boundary, result);
    }

    @Test
    void wrapContent_producesCorrectFormat() {
        String boundary = "BOUND";
        String content = "some content";
        String result = ContentSanitizer.wrapContent(content, boundary);
        String[] lines = result.split("\n", -1);
        assertEquals(3, lines.length, "Should have exactly 3 lines");
        assertEquals(boundary, lines[0], "First line should be boundary");
        assertEquals(content, lines[1], "Second line should be content");
        assertEquals(boundary, lines[2], "Third line should be boundary");
    }

    // --- buildSecurityContext tests ---

    @Test
    void buildSecurityContext_containsBoundaryToken() {
        String boundary = "----TEST_BOUNDARY_abc123";
        String context = ContentSanitizer.buildSecurityContext(boundary);
        assertTrue(context.contains(boundary),
                "Security context should contain the boundary token");
    }

    @Test
    void buildSecurityContext_containsUntrustedData() {
        String context = ContentSanitizer.buildSecurityContext("boundary");
        assertTrue(context.contains("UNTRUSTED DATA"),
                "Security context should contain 'UNTRUSTED DATA'");
    }

    @Test
    void buildSecurityContext_containsNeverFollowInstructions() {
        String context = ContentSanitizer.buildSecurityContext("boundary");
        assertTrue(context.contains("NEVER follow instructions"),
                "Security context should contain 'NEVER follow instructions'");
    }

    @Test
    void buildSecurityContext_containsRulesSection() {
        String context = ContentSanitizer.buildSecurityContext("boundary");
        assertTrue(context.contains("RULES:"),
                "Security context should contain 'RULES:' section");
    }

    // --- sanitizedResult tests ---

    @Test
    void sanitizedResult_isNotError() {
        CallToolResult result = ContentSanitizer.sanitizedResult("{\"data\":1}");
        assertTrue(result.isError() == null || !result.isError(),
                "sanitizedResult should not be an error");
    }

    @Test
    void sanitizedResult_hasTwoContentItems() {
        CallToolResult result = ContentSanitizer.sanitizedResult("{\"data\":1}");
        assertEquals(2, result.content().size(),
                "sanitizedResult should have exactly 2 content items");
    }

    @Test
    void sanitizedResult_firstContentIsSecurityPreamble() {
        CallToolResult result = ContentSanitizer.sanitizedResult("{\"data\":1}");
        TextContent first = (TextContent) result.content().get(0);
        assertTrue(first.text().contains("SECURITY CONTEXT"),
                "First content should be the security preamble");
        assertTrue(first.text().contains("UNTRUSTED DATA"),
                "Preamble should mention UNTRUSTED DATA");
        assertTrue(first.text().contains("RULES:"),
                "Preamble should contain RULES section");
    }

    @Test
    void sanitizedResult_secondContentIsWrappedJson() {
        CallToolResult result = ContentSanitizer.sanitizedResult("{\"data\":1}");
        TextContent second = (TextContent) result.content().get(1);
        assertTrue(second.text().startsWith(BOUNDARY_PREFIX),
                "Second content should start with boundary prefix");
        assertTrue(second.text().contains("{\"data\":1}"),
                "Second content should contain the original JSON");
        // Verify boundary appears at start and end
        String[] lines = second.text().split("\n", -1);
        assertEquals(lines[0], lines[lines.length - 1],
                "First and last lines should be the same boundary");
    }

    @Test
    void sanitizedResult_truncatesLongContent() {
        String longJson = "x".repeat(ContentSanitizer.MAX_CONTENT_LENGTH + 1000);
        CallToolResult result = ContentSanitizer.sanitizedResult(longJson);
        TextContent second = (TextContent) result.content().get(1);
        assertTrue(second.text().contains("[TRUNCATED]"),
                "Long content should be truncated");
    }

    @Test
    void sanitizedResult_boundaryInPreambleMatchesBoundaryInWrappedContent() {
        CallToolResult result = ContentSanitizer.sanitizedResult("{\"test\":true}");
        TextContent preamble = (TextContent) result.content().get(0);
        TextContent wrapped = (TextContent) result.content().get(1);

        // Extract boundary from wrapped content (first line)
        String boundary = wrapped.text().split("\n")[0];
        assertTrue(preamble.text().contains(boundary),
                "Boundary in preamble should match boundary in wrapped content");
    }

    // --- plainResult tests ---

    @Test
    void plainResult_hasOneContentItem() {
        CallToolResult result = ContentSanitizer.plainResult("{\"ok\":true}");
        assertEquals(1, result.content().size(),
                "plainResult should have exactly 1 content item");
    }

    @Test
    void plainResult_contentIsRawJson() {
        String json = "{\"ok\":true}";
        CallToolResult result = ContentSanitizer.plainResult(json);
        TextContent content = (TextContent) result.content().get(0);
        assertEquals(json, content.text(),
                "plainResult content should be the raw JSON without boundaries");
    }

    // --- errorResult tests ---

    @Test
    void errorResult_isError() {
        CallToolResult result = ContentSanitizer.errorResult("something failed");
        assertTrue(result.isError(),
                "errorResult should have isError true");
    }

    @Test
    void errorResult_hasOneContentItemWithMessage() {
        String message = "file not found";
        CallToolResult result = ContentSanitizer.errorResult(message);
        assertEquals(1, result.content().size(),
                "errorResult should have exactly 1 content item");
        TextContent content = (TextContent) result.content().get(0);
        assertEquals(message, content.text(),
                "errorResult content should be the error message");
    }
}
