package com.example.jobrecommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AdzunaJobSearch {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public List<Job> search(String query, String location, String countryCode) throws IOException, InterruptedException {
        String appId = System.getenv("ADZUNA_APP_ID");
        String appKey = System.getenv("ADZUNA_APP_KEY");
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            throw new IOException("Set ADZUNA_APP_ID and ADZUNA_APP_KEY environment variables. See README for setup.");
        }
        String country = countryCode == null || countryCode.isBlank() ? "in" : countryCode.trim().toLowerCase();
        String endpoint = "https://api.adzuna.com/v1/api/jobs/" + country + "/search/1?app_id=" + encode(appId)
                + "&app_key=" + encode(appKey) + "&results_per_page=50&what=" + encode(query.trim())
                + "&content-type=application/json" + (location == null || location.isBlank() ? "" : "&where=" + encode(location.trim()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IOException("Adzuna job search returned HTTP " + response.statusCode() + ". Check your API credentials and query.");

        JsonNode root = JSON.readTree(response.body());
        JsonNode results = root.path("results");
        List<Job> jobs = new ArrayList<>();
        if (results.isArray()) for (JsonNode item : results) {
            String title = text(item.path("title"));
            String company = text(item.path("company").path("display_name"));
            String place = text(item.path("location").path("display_name"));
            String description = text(item.path("description"));
            String url = text(item.path("redirect_url"));
            jobs.add(new Job(company, title, place, salary(item, country), description, url,
                    Set.of(), ResumeService.extractExperienceYears(description)));
        }
        return jobs;
    }

    private static String salary(JsonNode item, String country) {
        JsonNode minimum = item.path("salary_min");
        JsonNode maximum = item.path("salary_max");
        if (!minimum.isNumber() && !maximum.isNumber()) return "Not listed";
        String currency = switch (country.toLowerCase()) {
            case "in" -> "₹";
            case "us" -> "$";
            case "gb" -> "£";
            case "ca" -> "C$";
            case "au" -> "A$";
            default -> "";
        };
        return currency + (minimum.isNumber() ? String.format("%,.0f", minimum.asDouble()) : "?") + " – "
                + currency + (maximum.isNumber() ? String.format("%,.0f", maximum.asDouble()) : "?");
    }

    private static String text(JsonNode node) { return node.isTextual() ? node.asText() : ""; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
