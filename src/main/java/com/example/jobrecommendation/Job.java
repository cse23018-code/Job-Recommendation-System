package com.example.jobrecommendation;

import java.util.Set;

public record Job(String company, String title, String location, String salaryRange,
                  String description, String applyUrl, Set<String> skills, Integer requiredExperienceYears) {
    public Job {
        company = clean(company, "Company not listed");
        title = clean(title, "Untitled role");
        location = clean(location, "Location not listed");
        salaryRange = clean(salaryRange, "Not listed");
        description = description == null ? "" : description;
        applyUrl = applyUrl == null ? "" : applyUrl;
        skills = Set.copyOf(skills);
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
