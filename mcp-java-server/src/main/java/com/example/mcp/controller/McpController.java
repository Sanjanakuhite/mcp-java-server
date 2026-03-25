package com.example.mcp.controller;

import com.example.mcp.dto.JsonRpcRequest;
import com.example.mcp.dto.JsonRpcResponse;
import com.example.mcp.model.TextContent;
import com.example.mcp.model.ToolDefinition;
import com.example.mcp.model.ToolResult;
import com.example.mcp.service.ToolService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final ToolService toolService;
    private final ObjectMapper objectMapper;
    private final String allowedOrigins;

    public McpController(
            ToolService toolService,
            ObjectMapper objectMapper,
            @Value("${mcp.allowed-origins:*}") String allowedOrigins
    ) {
        this.toolService = toolService;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "Origin", required = false) String origin) throws IOException {
        validateOrigin(origin);

        SseEmitter emitter = new SseEmitter(0L);
        emitter.send(SseEmitter.event()
                .name("ready")
                .data(Map.of(
                        "message", "MCP endpoint is ready",
                        "timestamp", Instant.now().toString()
                )));
        return emitter;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handle(
            @RequestBody JsonRpcRequest request,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = "Accept", required = false) String accept
    ) {
        try {
            validateOrigin(origin);
            validateJsonRpc(request);
            validateAcceptHeader(accept);

            if (request.isNotification()) {
                return handleNotification(request);
            }

            return handleRequest(request);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Forbidden origin:")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(JsonRpcResponse.error(request != null ? request.getId() : null, -32003, e.getMessage()));
            }
            return ResponseEntity.badRequest().body(JsonRpcResponse.error(request != null ? request.getId() : null, -32602, e.getMessage()));
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.ok(JsonRpcResponse.error(request.getId(), -32601, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(JsonRpcResponse.error(request != null ? request.getId() : null, -32603,
                            e.getMessage() != null ? e.getMessage() : "Internal server error"));
        }
    }

    private ResponseEntity<?> handleNotification(JsonRpcRequest request) {
        if ("notifications/initialized".equals(request.getMethod())) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.accepted().build();
    }

    private ResponseEntity<JsonRpcResponse> handleRequest(JsonRpcRequest request) throws JsonProcessingException {
        return switch (request.getMethod()) {
            case "initialize" -> ResponseEntity.ok(JsonRpcResponse.success(request.getId(), buildInitializeResult()));
            case "ping" -> ResponseEntity.ok(JsonRpcResponse.success(request.getId(), Map.of())) ;
            case "tools/list" -> ResponseEntity.ok(JsonRpcResponse.success(request.getId(), Map.of("tools", buildTools())));
            case "tools/call" -> ResponseEntity.ok(JsonRpcResponse.success(request.getId(), executeTool(request.getParams())));
            default -> throw new UnsupportedOperationException("Method not found: " + request.getMethod());
        };
    }

    private Map<String, Object> buildInitializeResult() {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "mcp-java-server");
        serverInfo.put("version", "1.0.0");
        serverInfo.put("title", "Spring Boot MCP Server");

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        result.put("instructions", "Call tools/list to discover tools, then tools/call with the selected tool name and arguments.");
        return result;
    }

    private List<ToolDefinition> buildTools() {
        ToolDefinition discoverApis = new ToolDefinition(
                "discover_website_apis",
                "Discover Website APIs",
                "Discover likely API, Swagger, OpenAPI, GraphQL, or versioned endpoints from a website.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "website_url", Map.of(
                                        "type", "string",
                                        "description", "Website URL to scan, for example https://example.com"
                                )
                        ),
                        "required", List.of("website_url")
                )
        );

        ToolDefinition monitorApiBatch = new ToolDefinition(
                "monitor_api_batch",
                "Monitor API Batch",
                "Check health, latency, and content type for a batch of API URLs.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "api_batch", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string"),
                                        "description", "List of full API URLs"
                                )
                        ),
                        "required", List.of("api_batch")
                )
        );

        return List.of(discoverApis, monitorApiBatch);
    }

    private ToolResult executeTool(Map<String, Object> params) throws JsonProcessingException {
        if (params == null) {
            throw new IllegalArgumentException("params is required");
        }

        String name = valueAsString(params.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw
                : Collections.emptyMap();

        return switch (name) {
            case "discover_website_apis" -> successResult(toolService.discoverApis(valueAsString(arguments.get("website_url"))));
            case "monitor_api_batch" -> successResult(toolService.monitorBatch(stringList(arguments.get("api_batch"))));
            default -> new ToolResult(List.of(new TextContent("Unknown tool: " + name)), true);
        };
    }

    private ToolResult successResult(Object payload) throws JsonProcessingException {
        String text = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        return new ToolResult(List.of(new TextContent(text)), false);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("api_batch must be a list");
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private String valueAsString(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Required value is missing");
        }
        return String.valueOf(value);
    }

    private void validateJsonRpc(JsonRpcRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!JSON_RPC_VERSION.equals(request.getJsonrpc())) {
            throw new IllegalArgumentException("jsonrpc must be '2.0'");
        }
        if (request.getMethod() == null || request.getMethod().isBlank()) {
            throw new IllegalArgumentException("method is required");
        }
    }

    private void validateAcceptHeader(String accept) {
        if (accept == null || accept.isBlank()) {
            return;
        }
        String lower = accept.toLowerCase(Locale.ROOT);
        if (!lower.contains(MediaType.APPLICATION_JSON_VALUE) && !lower.contains(MediaType.TEXT_EVENT_STREAM_VALUE) && !lower.contains("*/*")) {
            throw new IllegalArgumentException("Accept header must allow application/json or text/event-stream");
        }
    }

    private void validateOrigin(String origin) {
        if (origin == null || origin.isBlank() || "*".equals(allowedOrigins)) {
            return;
        }

        List<String> allowed = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (!allowed.contains(origin)) {
            throw new IllegalArgumentException("Forbidden origin: " + origin);
        }
    }

}
