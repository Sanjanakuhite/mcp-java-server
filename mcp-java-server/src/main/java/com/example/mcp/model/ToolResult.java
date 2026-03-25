package com.example.mcp.model;

import java.util.List;

public class ToolResult {
    private List<TextContent> content;
    private boolean isError;

    public ToolResult() {
    }

    public ToolResult(List<TextContent> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    public List<TextContent> getContent() {
        return content;
    }

    public void setContent(List<TextContent> content) {
        this.content = content;
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean error) {
        isError = error;
    }
}
