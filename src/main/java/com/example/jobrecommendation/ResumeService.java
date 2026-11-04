package com.example.jobrecommendation;

import com.jiajia.common_object.Text;
import com.jiajia.core.JiaJiaOCR;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.exception.TikaException;
import org.apache.tika.Tika;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResumeService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Map<String, Set<String>> SKILL_ALIASES = aliases();
    private volatile JiaJiaOCR ocrEngine;
    private static final Pattern EXPLICIT_SKILLS = Pattern.compile("(?i)\\b(?:(?:technical|key|core|programming|required|essential|preferred)\\s+)?skills(?:\\s+and\\s+(?:tools|technologies))?\\s*[:\\-]\\s*(.{2,400})");
    private static final Pattern EXPERIENCE = Pattern.compile("(?i)(?:\\b(\\d{1,2})(?:\\s*[-–]\\s*\\d{1,2})?\\s*\\+?\\s*(?:years?|yrs?)\\b.{0,45}\\bexperience\\b|\\bexperience\\s*[:=\\-]?\\s*(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\b)");

    public String extractText(Path file) throws IOException, TikaException {
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) throw new IOException("Select a readable resume file.");
        long size = Files.size(file);
        if (size == 0 || size > MAX_FILE_SIZE) throw new IOException("Resume must be non-empty and no larger than 10 MB.");
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isImage(name)) return recognizeImage(javax.imageio.ImageIO.read(file.toFile()));

        String text = new Tika().parseToString(file.toFile()).trim();
        if (!name.endsWith(".pdf") && !text.isBlank()) return text;
        if (name.endsWith(".pdf")) {
            if (text.length() >= 150) return text;
            String ocrText = recognizePdf(file);
            return text.isBlank() ? ocrText : text + "\n" + ocrText;
        }
        throw new IOException("No readable text was found. Choose a text PDF/Word document or a clear PNG/JPG image.");
    }

    private static boolean isImage(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".tif") || name.endsWith(".tiff") || name.endsWith(".bmp");
    }

    private String recognizeImage(BufferedImage image) throws IOException {
        if (image == null) throw new IOException("This image format could not be opened. Use PNG, JPG, TIFF, or BMP.");
        return recognize(image);
    }

    private String recognizePdf(Path file) throws IOException {
        StringBuilder result = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            JiaJiaOCR ocr = ocrEngine();
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), 20);
            for (int page = 0; page < pages; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 180);
                result.append(recognize(ocr, image)).append('\n');
            }
        }
        if (result.toString().isBlank()) throw new IOException("No text could be recognized in this PDF.");
        return result.toString().trim();
    }

    private String recognize(BufferedImage image) throws IOException {
        return recognize(ocrEngine(), image);
    }

    // Loading the bundled models is expensive (hundreds of MB of native/off-heap memory),
    // so the engine is created once and reused for the lifetime of this ResumeService.
    private JiaJiaOCR ocrEngine() throws IOException {
        JiaJiaOCR engine = ocrEngine;
        if (engine == null) {
            synchronized (this) {
                engine = ocrEngine;
                if (engine == null) {
                    try {
                        engine = JiaJiaOCR.builder();
                    } catch (OutOfMemoryError | Exception failure) {
                        throw new IOException(ocrFailureMessage(failure), failure);
                    }
                    ocrEngine = engine;
                }
            }
        }
        return engine;
    }

    private String recognize(JiaJiaOCR ocr, BufferedImage image) throws IOException {
        try {
            StringBuilder result = new StringBuilder();
            for (Pair<Text, ?> line : ocr.recognizeGeneralText(image)) result.append(line.getLeft().getText()).append('\n');
            if (result.toString().isBlank()) throw new IOException("No text could be recognized. Use a sharper, well-lit image.");
            return result.toString().trim();
        } catch (IOException exception) {
            throw exception;
        } catch (OutOfMemoryError | Exception failure) {
            throw new IOException(ocrFailureMessage(failure), failure);
        }
    }

    private static String ocrFailureMessage(Throwable failure) {
        String detail = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        if (failure instanceof OutOfMemoryError || detail.toLowerCase(Locale.ROOT).contains("allocate")) {
            return "The bundled OCR engine ran out of memory. Close other applications and try again.";
        }
        return "The bundled OCR engine could not read this file: " + detail;
    }

    public static Set<String> extractSkills(String text) {
        String normalizedText = normalizeForMatching(text);
        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : SKILL_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                String normalizedAlias = normalizeForMatching(alias);
                Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizedAlias) + "(?![\\p{L}\\p{N}])");
                Matcher skillMention = pattern.matcher(normalizedText);
                while (skillMention.find()) {
                    if (!isNegatedMention(normalizedText, skillMention.start())) {
                        found.add(entry.getKey());
                        break;
                    }
                }
            }
        }
        Matcher explicitSection = EXPLICIT_SKILLS.matcher(text == null ? "" : text);
        while (explicitSection.find()) {
            String skillList = explicitSection.group(1).split("\\.\\s+", 2)[0];
            for (String phrase : skillList.split("[,;|•]")) {
                String skill = phrase.replaceAll("^[\\s*\\-–]+|[\\s.]+$", "").trim();
                if (skill.length() >= 2 && skill.length() <= 60 && skill.matches(".*[\\p{L}\\p{N}].*")) found.add(skill.toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(found);
    }

    public static Set<String> normalizeSkills(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String phrase = normalizeForMatching(value);
            if (phrase.isBlank()) continue;
            boolean aliasFound = false;
            for (Map.Entry<String, Set<String>> entry : SKILL_ALIASES.entrySet()) {
                if (entry.getValue().stream().anyMatch(alias -> normalizeForMatching(alias).equals(phrase))) {
                    normalized.add(entry.getKey());
                    aliasFound = true;
                    break;
                }
            }
            if (!aliasFound) normalized.add(phrase);
        }
        return Set.copyOf(normalized);
    }

    public static boolean isPreferredSkill(String description, String skill) {
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        String phrase = normalizeForMatching(skill);
        int index = text.indexOf(skill.toLowerCase(Locale.ROOT));
        if (index < 0) {
            for (String alias : SKILL_ALIASES.getOrDefault(skill.toLowerCase(Locale.ROOT), Set.of(skill))) {
                index = text.indexOf(alias.toLowerCase(Locale.ROOT));
                if (index >= 0) break;
            }
        }
        if (index < 0) index = text.indexOf(phrase);
        if (index < 0) return false;
        int sentenceStart = Math.max(Math.max(text.lastIndexOf('.', index), text.lastIndexOf(';', index)), text.lastIndexOf('\n', index)) + 1;
        int sentenceEnd = text.length();
        for (char boundary : new char[]{'.', ';', '\n'}) {
            int next = text.indexOf(boundary, index);
            if (next >= 0) sentenceEnd = Math.min(sentenceEnd, next);
        }
        String context = text.substring(sentenceStart, sentenceEnd);
        return context.matches("(?s).*(?:preferred|nice to have|desirable|bonus|optional).*?");
    }

    private static boolean isNegatedMention(String normalizedText, int start) {
        int sentenceStart = Math.max(Math.max(normalizedText.lastIndexOf('.', start), normalizedText.lastIndexOf(';', start)), normalizedText.lastIndexOf('\n', start)) + 1;
        String prefix = normalizedText.substring(sentenceStart, start);
        return prefix.matches("(?s).*(?:\\bno experience (?:with|in)|\\bnot (?:familiar with|experienced (?:with|in))|\\bunfamiliar with|\\bwithout experience (?:with|in)|\\bnever used|\\bhaven't used|\\bdid not use|\\bdidn't use|\\bno exposure to)\\s*(?:[\\p{L}\\p{N}+.#/-]+\\s+){0,2}");
    }

    public static Integer extractExperienceYears(String text) {
        Matcher matcher = EXPERIENCE.matcher(text == null ? "" : text);
        Integer years = null;
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int parsed = Integer.parseInt(value);
            years = years == null ? parsed : Math.max(years, parsed);
        }
        return years;
    }

    private static String normalizeForMatching(String value) {
        return (value == null ? "" : value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}+#./]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static Map<String, Set<String>> aliases() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        add(map, "java", "java", "core java", "j2se", "jdk");
        add(map, "python", "python", "python3", "python 3");
        add(map, "c", "c language", "c programming");
        add(map, "php", "php");
        add(map, "ruby", "ruby");
        add(map, "go", "golang", "go language");
        add(map, "swift", "swift");
        add(map, "scala", "scala");
        add(map, "rust", "rust", "rustlang");
        add(map, "r", "r programming", "r language");
        add(map, "matlab", "matlab");
        add(map, "dart", "dart");
        add(map, "flutter", "flutter");
        add(map, "spring boot", "spring boot", "springboot");
        add(map, "spring", "spring framework", "spring");
        add(map, "quarkus", "quarkus");
        add(map, "micronaut", "micronaut");
        add(map, "jpa", "jpa", "java persistence api");
        add(map, "sql", "sql", "structured query language");
        add(map, "machine learning", "machine learning", "ml", "scikit learn", "scikit-learn");
        add(map, "data science", "data science", "data scientist");
        add(map, "aws", "aws", "amazon web services");
        add(map, "azure", "azure", "microsoft azure");
        add(map, "gcp", "gcp", "google cloud platform", "google cloud");
        add(map, "react", "react", "react.js", "reactjs");
        add(map, "javascript", "javascript", "java script", "js", "ecmascript");
        add(map, "typescript", "typescript", "ts");
        add(map, "html", "html", "html5");
        add(map, "css", "css", "css3");
        add(map, "django", "django");
        add(map, "flask", "flask");
        add(map, "fastapi", "fastapi", "fast api");
        add(map, "hibernate", "hibernate");
        add(map, "c++", "c++", "cpp");
        add(map, "c#", "c#", "csharp", ".net");
        add(map, "kotlin", "kotlin");
        add(map, "docker", "docker", "containerization");
        add(map, "kubernetes", "kubernetes", "k8s");
        add(map, "mysql", "mysql");
        add(map, "postgresql", "postgresql", "postgres", "psql");
        add(map, "mongodb", "mongodb", "mongo db");
        add(map, "redis", "redis");
        add(map, "oracle database", "oracle database", "oracle db");
        add(map, "sql server", "sql server", "microsoft sql server");
        add(map, "tensorflow", "tensorflow", "tensor flow");
        add(map, "pytorch", "pytorch", "torch");
        add(map, "git", "git", "github", "gitlab", "version control");
        add(map, "linux", "linux", "ubuntu");
        add(map, "excel", "excel", "microsoft excel");
        add(map, "power bi", "power bi", "powerbi");
        add(map, "node.js", "node.js", "nodejs", "node js");
        add(map, "express.js", "express", "express.js", "expressjs");
        add(map, "angular", "angular", "angular.js", "angularjs");
        add(map, "vue.js", "vue", "vue.js", "vuejs");
        add(map, "next.js", "next.js", "nextjs", "next js");
        add(map, "rest api", "rest api", "restful api", "rest services");
        add(map, "graphql", "graphql", "graph ql");
        add(map, "jenkins", "jenkins");
        add(map, "terraform", "terraform");
        add(map, "ci/cd", "ci/cd", "continuous integration", "continuous delivery", "continuous deployment");
        add(map, "microservices", "microservices", "micro services");
        add(map, "data analysis", "data analysis", "data analytics");
        add(map, "apache spark", "apache spark", "spark", "pyspark");
        add(map, "hadoop", "hadoop", "apache hadoop");
        add(map, "kafka", "kafka", "apache kafka");
        add(map, "pandas", "pandas");
        add(map, "numpy", "numpy");
        add(map, "tableau", "tableau");
        add(map, "selenium", "selenium");
        add(map, "cypress", "cypress");
        add(map, "junit", "junit");
        add(map, "pytest", "pytest");
        add(map, "bash", "bash", "shell scripting");
        add(map, "github actions", "github actions");
        add(map, "owasp", "owasp", "owasp top 10");
        add(map, "penetration testing", "penetration testing", "pen testing", "pentesting");
        add(map, "business analysis", "business analysis", "business analyst");
        add(map, "project management", "project management", "project manager");
        add(map, "leadership", "leadership", "team leadership");
        add(map, "communication", "communication skills", "verbal communication", "written communication");
        add(map, "agile", "agile", "scrum", "kanban");
        return Map.copyOf(map);
    }

    private static void add(Map<String, Set<String>> map, String canonical, String... aliases) {
        map.put(canonical, Set.of(aliases));
    }
}
