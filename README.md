# Job Recommendation System

A small desktop job hunting tool. It reads a resume on your computer, searches current listings through Adzuna, ranks the results against skills and experience, and opens the listing's application page. Results show match scores without listing the detected or missing skill names.

## What it does

- Reads text based PDF, DOCX, DOC, and TXT resumes.
- Reads PNG, JPG/JPEG, TIFF, and BMP resumes with a bundled local OCR engine. Scanned PDFs use the same local OCR fallback (up to 20 pages).
- Extracts skills across the full resume and job description, using aliases such as `JS`/`JavaScript`, `K8s`/`Kubernetes`, and `Amazon Web Services`/`AWS`. It also matches unfamiliar skill names when they appear in explicit `Skills:` sections.
- Detects explicitly stated experience years, compares them with years stated in job descriptions, and shows the result separately from the skill match score.
- Searches Adzuna for live listings by role/keywords, location, and country. Double click a result to open its application link.

The resume is processed locally and is not uploaded by this app. The role/keywords and location you type are sent to Adzuna as a search request.

## Run it

Requirements to build: Java 17 or newer and an internet connection for the first build and first OCR run. The included Maven Wrapper downloads Maven and Java libraries; the English OCR model is included in the project. The OCR runtime automatically downloads its platform support on first use. Users do not need to install Maven, Tesseract, Python, or a separate OCR program. The OCR model plus inference libraries make the first download large. Running the packaged app requires Java 17 or newer, internet access for OCR/job searches, and the API credentials below.

1. Create a free Adzuna API account and obtain an `app_id` and `app_key` at [developer.adzuna.com](https://developer.adzuna.com/). Adzuna currently offers API keys without payment details for personal job search and evaluation; usage terms and limits apply and may change.
2. Set the credentials in PowerShell (replace the example values):

   ```powershell
   $env:ADZUNA_APP_ID = "your-app-id"
   $env:ADZUNA_APP_KEY = "your-app-key"
   ```

   Keep credentials out of source control. These session variables last only for the current PowerShell window.
3. From this folder, build the self-contained app:

   ```powershell
   .\mvnw.cmd clean package
   ```
4. Launch it:

   ```powershell
   java -jar target/job-recommendation-system-1.0.0.jar
   ```

## Use it

1. In **Your resume**, choose a PDF, DOC, DOCX, TXT, or supported image resume, then select **Prepare profile**. The profile is prepared locally; the app shows a short readiness summary without exposing a skill list.
2. In **Find your next role**, enter a role or search phrase and location, choose a country, then select **Find matching jobs**.
3. Compare the weighted match percentage and experience note. Double click a row to open the listing.

## How matching works

The results show a match score and experience note without displaying extracted or missing skill lists. The score weights required skills twice as much as preferred skills. When both resume and job state experience years, the final score combines 80% weighted skill overlap with 20% experience fit; otherwise it uses the skill score alone. Skill overlap includes known aliases plus unfamiliar terms found under explicit skills headings. The matcher also ignores common negative wording such as “no experience with Java.”

This is a transparent rules based score, not an AI hiring decision or a guarantee of qualification. No finite parser can recognize every skill name, abbreviation, or implied capability. Unfamiliar terms outside clear skills sections may be missed, and low quality scans or ambiguous experience phrasing can lower accuracy.

Adzuna supplies the live vacancy data. The app displays the listing data and links users to the source to apply. Use is subject to [Adzuna API terms](https://developer.adzuna.com/docs/terms_of_service) and the [Search API documentation](https://developer.adzuna.com/docs/search).

## Project structure

- `JobRecommendationApp` — Swing user interface and background tasks
- `ResumeService` — local text/OCR parsing, skills, and experience extraction
- `AdzunaJobSearch` — live API integration and JSON mapping
- `JobMatcher` — skill overlap ranking and experience comparison
- `Job` / `JobMatch` — typed domain records

The OCR package is JiaJiaOCR 1.0.2, distributed under Apache License 2.0. Its release JAR is included under `lib/` because the upstream release is not hosted on Maven Central; its license is included beside it. The app uses the package locally and does not send resume images to an OCR service.

The old archive had duplicated nested copies, unused scaffolding, fake matches, placeholder methods, and hard coded database credentials. This clean version removes the abandoned login/database scaffolding and sample job catalog so the main flow is clear and does not require a MySQL server.
