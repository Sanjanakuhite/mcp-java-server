# Spring Boot Remote MCP Server

This is a minimal remote MCP server built with Spring Boot 3 and Java 17.

## What it supports

- Streamable HTTP style endpoint at `/mcp`
- JSON-RPC 2.0 request handling
- `initialize`
- `notifications/initialized`
- `ping`
- `tools/list`
- `tools/call`
- Two sample tools:
  - `discover_website_apis`
  - `monitor_api_batch`

## Run locally

```bash
mvn spring-boot:run
```

The server starts on:

```text
http://localhost:8080/mcp
```

## Render deployment

Set these values in Render:

- Build Command:

```text
mvn clean package -DskipTests
```

- Start Command:

```text
java -jar target/mcp-java-server-1.0.0.jar
```

Optional environment variable:

```text
MCP_ALLOWED_ORIGINS=*
```

Or restrict it:

```text
MCP_ALLOWED_ORIGINS=https://chatgpt.com,https://claude.ai
```

## Test initialize

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-06-18",
      "capabilities": {},
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    }
  }'
```

## Test tools/list

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list"
  }'
```

## Test discover_website_apis

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "discover_website_apis",
      "arguments": {
        "website_url": "https://example.com"
      }
    }
  }'
```

## Test monitor_api_batch

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "monitor_api_batch",
      "arguments": {
        "api_batch": [
          "https://jsonplaceholder.typicode.com/users/1",
          "https://httpbin.org/get"
        ]
      }
    }
  }'
```
