package com.example.jobrecommendation;

import java.util.Set;

public record JobMatch(Job job, int matchPercentage, Set<String> missingSkills, String experienceNote) {
    public JobMatch {
        if (matchPercentage < 0 || matchPercentage > 100) throw new IllegalArgumentException("Match percentage must be between 0 and 100");
        missingSkills = Set.copyOf(missingSkills);
        experienceNote = experienceNote == null ? "" : experienceNote;
    }
}
