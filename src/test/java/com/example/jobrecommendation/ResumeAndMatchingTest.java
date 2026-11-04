package com.example.jobrecommendation;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResumeAndMatchingTest {
    @TempDir Path tempDir;

    private final ResumeService resumeService = new ResumeService();

    @Test
    void extractsSkillsAcrossResumeAndRecognizesAliases() {
        Set<String> skills = ResumeService.extractSkills(
                "Experience: built APIs with JavaScript, JS, K8s, and Amazon Web Services. " +
                        "Also used SQL, PowerBI, and machine learning.");

        assertTrue(skills.containsAll(Set.of("javascript", "kubernetes", "aws", "sql", "power bi", "machine learning")));
        assertEquals("java", ResumeService.normalizeSkills(Set.of("Core Java")).iterator().next());
        assertFalse(ResumeService.extractSkills("HTML is used for web page structure.").contains("machine learning"));
        assertFalse(ResumeService.extractSkills("No experience with Java; currently using Python on projects.").contains("java"));
        assertTrue(ResumeService.extractSkills("No experience with Java; currently using Python on projects.").contains("python"));
    }

    @Test
    void extractsExplicitExperienceFromCommonPhrasings() {
        assertEquals(3, ResumeService.extractExperienceYears("3-5 years of professional experience required"));
        assertEquals(4, ResumeService.extractExperienceYears("Experience: 4+ yrs"));
        assertNull(ResumeService.extractExperienceYears("Graduated in 2023; no experience stated"));
    }

    @Test
    void ranksJobsAgainstTheFullDescriptionAndSeparatesExperience() {
        Job job = new Job("Example Co", "Developer", "Remote", "Not listed",
                "Java, JavaScript and SQL. Requires 3 years of experience.", "https://example.com/apply", Set.of(), 3);

        JobMatch match = JobMatcher.rank(List.of(job), Set.of("Java", "JS"), 2).get(0);

        assertEquals(67, match.matchPercentage());
        assertEquals(Set.of("sql"), match.missingSkills());
        assertEquals("May need 3+ years", match.experienceNote());
    }

    @Test
    void matchesUnfamiliarSkillsFromExplicitSkillSectionsAndWeightsPreferredSkillsLower() {
        String resume = "Core Skills: Salesforce, SAP S/4HANA";
        Job job = new Job("Example Co", "ERP Analyst", "Remote", "Not listed",
                "Required Skills: Salesforce, SAP S/4HANA, Tableau. Kubernetes is nice to have.",
                "https://example.com/apply", Set.of(), null);

        Set<String> candidateSkills = ResumeService.extractSkills(resume);
        JobMatch match = JobMatcher.rank(List.of(job), candidateSkills, null).get(0);

        assertEquals(57, match.matchPercentage());
        assertTrue(match.missingSkills().contains("tableau"));
        assertTrue(match.missingSkills().contains("kubernetes"));
    }

    @Test
    void readsTextResumeAndExtractsProfile() throws Exception {
        Path resume = tempDir.resolve("resume.txt");
        java.nio.file.Files.writeString(resume, "Java developer with 2 years of experience. Uses Spring Boot and MySQL.");

        String text = resumeService.extractText(resume);

        assertTrue(ResumeService.extractSkills(text).containsAll(Set.of("java", "spring boot", "mysql")));
        assertEquals(2, ResumeService.extractExperienceYears(text));
    }

    @Test
    void recognizesImageResumeLocally() throws Exception {
        Path image = tempDir.resolve("resume.png");
        ImageIO.write(resumeImage(), "png", image.toFile());

        String text = resumeService.extractText(image);

        assertTrue(text.toUpperCase().contains("PYTHON"), "OCR should find the clear printed skill in the image");
        assertTrue(ResumeService.extractSkills(text).contains("python"));
    }

    @Test
    void fallsBackToLocalOcrForScannedPdf() throws Exception {
        Path pdf = tempDir.resolve("scanned-resume.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            var image = LosslessFactory.createFromImage(document, resumeImage());
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 40, 400, 520, 180);
            }
            document.save(pdf.toFile());
        }

        String text = resumeService.extractText(pdf);

        assertTrue(text.toUpperCase().contains("PYTHON"), "OCR fallback should read the scanned PDF page");
    }

    private static BufferedImage resumeImage() {
        BufferedImage image = new BufferedImage(1500, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font("Arial", Font.BOLD, 104));
            graphics.drawString("PYTHON", 70, 185);
            graphics.setFont(new Font("Arial", Font.PLAIN, 60));
            graphics.drawString("2 years experience", 70, 300);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
