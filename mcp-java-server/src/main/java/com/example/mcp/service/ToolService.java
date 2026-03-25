package com.example.mcp.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ToolService {

    private final RestTemplate restTemplate;

    public ToolService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> discoverApis(String websiteUrl) {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            throw new IllegalArgumentException("website_url is required");
        }

        String normalizedUrl = normalizeUrl(websiteUrl);
        Document document;
        try {
            document = Jsoup.connect(normalizedUrl)
                    .userAgent("Mozilla/5.0")
                    .header("Accept", "text/html")
                    .header("Accept-Language", "en-US")
                    .timeout(60000)
                    .get();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to fetch website: " + e.getMessage());
        }

        Set<String> candidates = new LinkedHashSet<>();
        for (Element element : document.select("a[href], link[href], script[src]")) {
            String link = element.hasAttr("href") ? element.absUrl("href") : element.absUrl("src");
            if (link == null || link.isBlank()) {
                continue;
            }

            String lower = link.toLowerCase();

            // ✅ Updated filter (clean API detection)
            if (lower.contains("/api") || lower.contains("/v1") || lower.contains("/v2")
                    || lower.contains("swagger") || lower.contains("openapi")
                    || lower.contains("graphql")
                    || lower.endsWith(".json")) {
                candidates.add(link);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("website", normalizedUrl);
        result.put("title", document.title());
        result.put("candidateApis", new ArrayList<>(candidates));
        result.put("count", candidates.size());
        return result;
    }

    public List<Map<String, Object>> monitorBatch(List<String> apiBatch) {
        if (apiBatch == null || apiBatch.isEmpty()) {
            throw new IllegalArgumentException("api_batch must contain at least one URL");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (String api : apiBatch) {
            results.add(checkApi(api));
        }
        return results;
    }

    private Map<String, Object> checkApi(String api) {
        String normalizedUrl = normalizeUrl(api);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("api", normalizedUrl);

        Instant start = Instant.now();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response;
            try {
                response = restTemplate.exchange(normalizedUrl, HttpMethod.HEAD, entity, String.class);
            } catch (RestClientException headError) {
                response = restTemplate.exchange(normalizedUrl, HttpMethod.GET, entity, String.class);
            }

            long latency = Duration.between(start, Instant.now()).toMillis();
            row.put("statusCode", response.getStatusCode().value());
            row.put("latencyMs", latency);
            row.put("health", response.getStatusCode().is2xxSuccessful() ? "HEALTHY" : "UNHEALTHY");
            row.put("contentType", response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString()
                    : "unknown");
            row.put("error", "");
            return row;
        } catch (Exception e) {
            long latency = Duration.between(start, Instant.now()).toMillis();
            row.put("statusCode", 0);
            row.put("latencyMs", latency);
            row.put("health", "UNHEALTHY");
            row.put("contentType", "unknown");
            row.put("error", e.getMessage());
            return row;
        }
    }

    private String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("URL is required");
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        URI.create(value);
        return value;
    }
}