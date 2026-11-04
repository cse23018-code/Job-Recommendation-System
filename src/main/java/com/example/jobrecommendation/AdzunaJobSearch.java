package com.example.jobrecommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates randomized sample job listings instead of calling a live job-search API.
 * Kept under the original class name so {@link JobRecommendationApp} needs no changes.
 */
public final class AdzunaJobSearch {
    private static final int RESULTS_PER_SEARCH = 25;

    private static final String[] COMPANY_NAMES = {
            "Northwind Systems", "Bluepeak Technologies", "Vertex Labs", "Riverstone Digital",
            "Clearwater Analytics", "Ironleaf Software", "Summit & Co", "Brightline Studios",
            "Fernhill Robotics", "Alpine Data Works", "Cobalt Cloud Partners", "Lumen Forge",
            "Orchard Platforms", "Granite Peak Tech", "Harborview Systems", "Nimbus Group",
            "Redstone Innovations", "Westfield Analytics", "Cascade Software Co", "Pinecrest Labs"
    };

    private static final String[] TITLE_LEVELS = {"Junior", "", "Senior", "Lead", "Staff"};

    private static final String[] SKILL_POOL = {
            "java", "python", "javascript", "typescript", "react", "node.js", "spring boot",
            "sql", "postgresql", "mongodb", "aws", "azure", "docker", "kubernetes", "git",
            "rest api", "graphql", "ci/cd", "machine learning", "pandas", "linux", "agile"
    };

    private final Random random = new Random();

    public List<Job> search(String query, String location, String countryCode) {
        String role = query == null || query.isBlank() ? "Software Engineer" : query.trim();
        String place = location == null || location.isBlank() ? "Remote" : location.trim();
        String country = countryCode == null || countryCode.isBlank() ? "in" : countryCode.trim().toLowerCase(Locale.ROOT);

        List<Job> jobs = new ArrayList<>(RESULTS_PER_SEARCH);
        for (int i = 0; i < RESULTS_PER_SEARCH; i++) {
            jobs.add(randomJob(role, place, country, i));
        }
        return jobs;
    }

    private Job randomJob(String role, String place, String country, int index) {
        String company = COMPANY_NAMES[random.nextInt(COMPANY_NAMES.length)];
        String level = TITLE_LEVELS[random.nextInt(TITLE_LEVELS.length)];
        String title = (level.isBlank() ? "" : level + " ") + capitalize(role);

        Set<String> skills = randomSkills();
        Integer requiredYears = switch (level) {
            case "Junior" -> random.nextInt(2);
            case "Senior", "Lead", "Staff" -> 4 + random.nextInt(6);
            default -> random.nextInt(4);
        };

        String description = "We are hiring a " + title + " to join our team in " + place + ". "
                + "Required skills: " + String.join(", ", skills) + ". "
                + (requiredYears == 0 ? "Open to candidates with no prior experience."
                : requiredYears + "+ years of relevant experience preferred.");

        String salary = salaryRange(country);
        String applyUrl = "https://jobs.example.test/listings/" + Integer.toHexString((company + title + index).hashCode());

        return new Job(company, title, place, salary, description, applyUrl, skills, requiredYears);
    }

    private Set<String> randomSkills() {
        List<String> pool = new ArrayList<>(List.of(SKILL_POOL));
        java.util.Collections.shuffle(pool, random);
        int count = 3 + random.nextInt(4);
        return pool.stream().limit(count).collect(Collectors.toUnmodifiableSet());
    }

    private String salaryRange(String country) {
        String currency = switch (country) {
            case "in" -> "₹";
            case "us" -> "$";
            case "gb" -> "£";
            case "ca" -> "C$";
            case "au" -> "A$";
            default -> "";
        };
        int base = country.equals("in") ? 600_000 : 60_000;
        int min = base + random.nextInt(base);
        int max = min + base / 2 + random.nextInt(base);
        return currency + String.format("%,d", min) + " – " + currency + String.format("%,d", max);
    }

    private static String capitalize(String value) {
        if (value.isBlank()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
