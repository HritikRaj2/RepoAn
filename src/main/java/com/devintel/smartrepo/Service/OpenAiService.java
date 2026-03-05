package com.devintel.smartrepo.Service;

import com.devintel.smartrepo.Entity.CommitQuality;
import com.devintel.smartrepo.Entity.FileRisk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenAiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:}")
    private String apiKey;

    public OpenAiService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(5 * 1024 * 1024)
                )
                .build();
    }

    // ─── GENERATE DETAILED AI REVIEW ─────────────────────────────────────
    public String generateDetailedReview(int healthScore, String riskLevel,
                                         List<FileRisk> fileRisks,
                                         CommitQuality commitQuality,
                                         int totalSecuritySmells) {

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("⚠️ Gemini API key not configured, returning default summary");
            return generateDefaultSummary(healthScore, riskLevel, fileRisks,
                    commitQuality, totalSecuritySmells);
        }

        try {
            String prompt = buildDetailedPrompt(healthScore, riskLevel, fileRisks,
                    commitQuality, totalSecuritySmells);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.7,
                            "maxOutputTokens", 2048
                    )
            );

            String requestJson = objectMapper.writeValueAsString(requestBody);

            String response = webClient.post()
                    .uri("/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String summary = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            log.info("🤖 AI Detailed Review generated successfully");
            return summary.trim();

        } catch (Exception e) {
            log.error("❌ Gemini API failed: {}", e.getMessage());
            return generateDefaultSummary(healthScore, riskLevel, fileRisks,
                    commitQuality, totalSecuritySmells);
        }
    }

    // ─── BUILD DETAILED PROMPT WITH FILE-LEVEL DATA ──────────────────────
    private String buildDetailedPrompt(int healthScore, String riskLevel,
                                       List<FileRisk> fileRisks,
                                       CommitQuality commitQuality,
                                       int totalSecuritySmells) {

        StringBuilder fileDetails = new StringBuilder();
        for (FileRisk fr : fileRisks) {
            fileDetails.append(String.format(
                    "  - %s | LOC: %d | Complexity: %d | Security Smells: %d | Risk: %d/100 (%s)\n",
                    fr.getFile_path(),
                    fr.getLines_of_code() != null ? fr.getLines_of_code() : 0,
                    fr.getComplexity_score() != null ? fr.getComplexity_score() : 0,
                    fr.getSecurity_smells() != null ? fr.getSecurity_smells() : 0,
                    fr.getRiskScore() != null ? fr.getRiskScore() : 0,
                    fr.getRiskLevel() != null ? fr.getRiskLevel() : "LOW"
            ));
        }

        int totalCommits = commitQuality.getTotal_commits() != null ? commitQuality.getTotal_commits() : 0;
        int goodMessages = commitQuality.getGood_messages() != null ? commitQuality.getGood_messages() : 0;
        int badMessages  = commitQuality.getBad_messages() != null ? commitQuality.getBad_messages() : 0;
        int qualityScore = commitQuality.getQuality_score() != null ? commitQuality.getQuality_score() : 0;

        return String.format("""
            You are a senior code reviewer and software architect. Perform a DETAILED code review of this GitHub repository.

            ═══════════════════════════════════════════
            REPOSITORY HEALTH REPORT
            ═══════════════════════════════════════════
            Overall Health Score: %d/100
            Risk Level: %s
            Total Security Smells: %d

            ─── FILE-BY-FILE ANALYSIS ───
            %s
            ─── COMMIT ANALYSIS ───
            Total Commits: %d
            Good Messages: %d | Bad Messages: %d
            Commit Quality Score: %d/100

            ═══════════════════════════════════════════

            Now provide a DETAILED review with these EXACT sections:

            ## 🏥 Overall Repository Health
            Give a 2-3 sentence assessment of the repository's overall health.

            ## 📁 File-by-File Review
            For EACH file that has complexity > 2 OR security smells > 0 OR risk score > 20:
            - State the file name
            - Explain what issues were found
            - Give SPECIFIC suggestions to fix (e.g., "Extract the nested if-else block on line handling into a separate method" or "Remove hardcoded password string and use environment variables instead")

            For files that are clean, group them and say they look good.

            ## 🔒 Security Review
            List any security concerns found. For each:
            - Which file has the issue
            - What the security smell is (hardcoded credentials, SQL injection risk, etc.)
            - How to fix it with a specific code change
            If no security issues, say the codebase is clean.

            ## 📝 Commit Quality Review
            Analyze the commit message quality:
            - What percentage are good vs bad
            - Give examples of what good commit messages should look like for this project
            - Suggest a commit message convention (e.g., "feat: add user authentication")

            ## 🎯 Top 5 Recommendations
            List the top 5 actionable improvements ranked by priority:
            1. (most critical)
            2. ...
            3. ...
            4. ...
            5. (nice to have)

            ## 📊 Score Breakdown
            Explain how the %d/100 score was calculated and what would bring it to 95+.

            Keep the tone professional but friendly. Use markdown formatting.
            Be SPECIFIC — mention actual file names and concrete suggestions, not vague advice.
            """,
                healthScore, riskLevel, totalSecuritySmells,
                fileDetails.toString(),
                totalCommits, goodMessages, badMessages, qualityScore,
                healthScore);
    }

    // ─── FALLBACK DETAILED SUMMARY ───────────────────────────────────────
    private String generateDefaultSummary(int healthScore, String riskLevel,
                                          List<FileRisk> fileRisks,
                                          CommitQuality commitQuality,
                                          int totalSecuritySmells) {

        StringBuilder sb = new StringBuilder();

        // ── Overall Health ──────────────────────────────���────────────
        sb.append("## 🏥 Overall Repository Health\n");
        if (healthScore >= 70) {
            sb.append("This repository demonstrates good overall health with a score of ")
                    .append(healthScore).append("/100. ");
        } else if (healthScore >= 40) {
            sb.append("This repository has moderate health concerns with a score of ")
                    .append(healthScore).append("/100. ");
        } else {
            sb.append("This repository has significant health issues with a score of ")
                    .append(healthScore).append("/100. ");
        }
        sb.append("Risk level: ").append(riskLevel).append(".\n\n");

        // ── File Review ──────────────────────────────────────────────
        sb.append("## 📁 File-by-File Review\n");
        boolean hasIssues = false;
        for (FileRisk fr : fileRisks) {
            int risk = fr.getRiskScore() != null ? fr.getRiskScore() : 0;
            int complexity = fr.getComplexity_score() != null ? fr.getComplexity_score() : 0;
            int smells = fr.getSecurity_smells() != null ? fr.getSecurity_smells() : 0;

            if (risk > 20 || complexity > 2 || smells > 0) {
                hasIssues = true;
                sb.append("- **").append(fr.getFile_path()).append("**");
                sb.append(" (Risk: ").append(risk).append("/100)");
                if (complexity > 2) {
                    sb.append(" — High complexity (").append(complexity)
                            .append("). Consider breaking into smaller methods.");
                }
                if (smells > 0) {
                    sb.append(" — ").append(smells)
                            .append(" security smell(s) detected. Review for hardcoded credentials or injection risks.");
                }
                sb.append("\n");
            }
        }
        if (!hasIssues) {
            sb.append("All files have low risk scores. Code is clean and well-structured.\n");
        }
        sb.append("\n");

        // ── Security Review ──────────────────────────────────────────
        sb.append("## 🔒 Security Review\n");
        if (totalSecuritySmells > 0) {
            sb.append(totalSecuritySmells).append(" security smell(s) detected. ")
                    .append("Review files flagged above for hardcoded passwords, API keys, or SQL injection patterns. ")
                    .append("Use environment variables for secrets.\n\n");
        } else {
            sb.append("No security vulnerabilities detected. Codebase is clean.\n\n");
        }

        // ── Commit Quality ───────────────────────────────────��───────
        int qualityScore = commitQuality.getQuality_score() != null ? commitQuality.getQuality_score() : 0;
        int totalCommits = commitQuality.getTotal_commits() != null ? commitQuality.getTotal_commits() : 0;
        int goodMessages = commitQuality.getGood_messages() != null ? commitQuality.getGood_messages() : 0;
        int badMessages  = commitQuality.getBad_messages() != null ? commitQuality.getBad_messages() : 0;

        sb.append("## 📝 Commit Quality Review\n");
        sb.append("Analyzed ").append(totalCommits).append(" commits: ")
                .append(goodMessages).append(" good, ").append(badMessages).append(" bad. ");
        sb.append("Quality score: ").append(qualityScore).append("/100. ");
        if (qualityScore < 70) {
            sb.append("Use descriptive messages like: \"feat: add JWT authentication\" or \"fix: resolve null pointer in UserService\".\n\n");
        } else {
            sb.append("Good commit message practices!\n\n");
        }

        // ── Top Recommendations ──────────────────────────────────────
        sb.append("## 🎯 Top Recommendations\n");
        sb.append("1. ").append(qualityScore < 70 ? "Improve commit message quality — use conventional commits (feat/fix/refactor)" : "Maintain current commit quality standards").append("\n");
        sb.append("2. ").append(totalSecuritySmells > 0 ? "Fix security smells — move secrets to environment variables" : "Continue following secure coding practices").append("\n");
        sb.append("3. Add unit tests to increase code confidence\n");
        sb.append("4. Add README documentation with setup instructions\n");
        sb.append("5. Set up CI/CD pipeline for automated testing\n");

        return sb.toString();
    }
}