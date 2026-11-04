package com.example.jobrecommendation;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class JobMatcher {
    private JobMatcher() { }

    public static List<JobMatch> rank(List<Job> jobs, Set<String> candidateSkills, Integer candidateExperienceYears) {
        Set<String> candidate = ResumeService.normalizeSkills(candidateSkills);
        return jobs.stream().map(job -> {
            Set<String> required = new TreeSet<>(ResumeService.normalizeSkills(ResumeService.extractSkills(job.description())));
            required.addAll(ResumeService.normalizeSkills(ResumeService.extractSkills(job.title())));
            required.addAll(ResumeService.normalizeSkills(job.skills()));
            Set<String> missing = new TreeSet<>(required);
            missing.removeAll(candidate);
            int totalWeight = 0;
            int matchedWeight = 0;
            for (String skill : required) {
                int weight = ResumeService.isPreferredSkill(job.description(), skill) ? 1 : 2;
                totalWeight += weight;
                if (!missing.contains(skill)) matchedWeight += weight;
            }
            int skillScore = totalWeight == 0 ? 0 : (int) Math.round(100.0 * matchedWeight / totalWeight);
            int percent = skillScore;
            if (!required.isEmpty() && candidateExperienceYears != null && job.requiredExperienceYears() != null) {
                double experienceScore = job.requiredExperienceYears() == 0 ? 100.0
                        : Math.min(100.0, 100.0 * candidateExperienceYears / job.requiredExperienceYears());
                percent = (int) Math.round(skillScore * 0.8 + experienceScore * 0.2);
            }
            String experience = job.requiredExperienceYears() == null || candidateExperienceYears == null
                    ? "Experience not specified" : candidateExperienceYears >= job.requiredExperienceYears()
                    ? "Experience meets listed requirement" : "May need " + job.requiredExperienceYears() + "+ years";
            return new JobMatch(job, percent, missing, experience);
        }).sorted(Comparator.comparingInt(JobMatch::matchPercentage).reversed()
                .thenComparing(match -> match.job().title(), String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
