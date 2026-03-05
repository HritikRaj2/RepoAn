package com.devintel.smartrepo.Service;

import com.devintel.smartrepo.Entity.CommitQuality;
import com.devintel.smartrepo.Entity.FileRisk;
import com.devintel.smartrepo.Entity.RepoAnalysis;
import com.devintel.smartrepo.Entity.User;
import com.devintel.smartrepo.Repository.CommitQualityRepository;
import com.devintel.smartrepo.Repository.FileRiskRepository;
import com.devintel.smartrepo.Repository.RepoAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoAnalyzerService {

    private final GitHubService gitHubService;
    private final FileParserService fileParserService;
    private final CommitService commitService;
    private final RepoAnalysisRepository repoAnalysisRepository;
    private final FileRiskRepository fileRiskRepository;
    private final CommitQualityRepository commitQualityRepository;

    // ─── MAIN PIPELINE: ANALYZE A REPO (ASYNC) ──────────────────────────
    @Async
    public CompletableFuture<RepoAnalysis> analyzeRepo(String repoUrl, User user) {
        log.info("🚀 Starting analysis for: {}", repoUrl);

        try {
            // ── Step 1: Parse the URL ────────────────────────────────
            String[] parsed = gitHubService.parseRepoUrl(repoUrl);
            String owner = parsed[0];
            String repo  = parsed[1];

            // ── Step 2: Create initial RepoAnalysis record ───────────
            RepoAnalysis analysis = new RepoAnalysis();
            analysis.setUser(user);
            analysis.setRepoUrl(repoUrl);
            analysis.setHealthScore(0);
            analysis.setRiskLevel(RepoAnalysis.RiskLevel.LOW);
            analysis = repoAnalysisRepository.save(analysis);
            log.info("📝 Created analysis record with id: {}", analysis.getId());

            // ── Step 3: Analyze source files ─────────────────────────
            List<FileRisk> fileRisks = analyzeFiles(owner, repo, analysis);
            log.info("📁 Analyzed {} files", fileRisks.size());

            // ── Step 4: Analyze commits ──────────────────────────────
            CommitQuality commitQuality = analyzeCommitQuality(owner, repo, analysis);
            log.info("📊 Commit quality score: {}", commitQuality.getQuality_score());

            // ── Step 5: Calculate final health score ─────────────────
            int healthScore = calculateHealthScore(fileRisks, commitQuality);
            RepoAnalysis.RiskLevel riskLevel = determineOverallRisk(healthScore);

            // ── Step 6: Update and save final result ─────────────────
            analysis.setHealthScore(healthScore);
            analysis.setRiskLevel(riskLevel);
            analysis = repoAnalysisRepository.save(analysis);

            log.info("✅ Analysis complete! Health: {}, Risk: {}", healthScore, riskLevel);
            return CompletableFuture.completedFuture(analysis);

        } catch (Exception e) {
            log.error("❌ Analysis failed for {}: {}", repoUrl, e.getMessage(), e);
            throw new RuntimeException("Analysis failed: " + e.getMessage(), e);
        }
    }

    // ─── ANALYZE ALL SOURCE FILES ────────────────────────────��───────────
    private List<FileRisk> analyzeFiles(String owner, String repo, RepoAnalysis analysis) {

        List<JsonNode> sourceFiles = gitHubService.getSourceFiles(owner, repo);
        List<FileRisk> fileRisks = new ArrayList<>();

        int fileCount = 0;
        int maxFiles = 50;  // Limit to avoid GitHub API rate limits

        for (JsonNode file : sourceFiles) {
            if (fileCount >= maxFiles) {
                log.warn("⚠️ File limit reached ({}), stopping file analysis", maxFiles);
                break;
            }

            String filePath = file.get("path").asText();
            String sha      = file.get("sha").asText();

            try {
                // Fetch file content from GitHub
                String content = gitHubService.getFileContent(owner, repo, sha);

                if (content != null && !content.isEmpty()) {
                    // Analyze with FileParserService
                    FileRisk fileRisk = fileParserService.analyzeFile(filePath, content, analysis);

                    // Save to database
                    fileRisk = fileRiskRepository.save(fileRisk);
                    fileRisks.add(fileRisk);
                    fileCount++;
                }
            } catch (Exception e) {
                log.warn("⚠️ Skipping file {}: {}", filePath, e.getMessage());
            }
        }

        return fileRisks;
    }

    // ─── ANALYZE COMMIT QUALITY ──────────────────────────────────────────
    private CommitQuality analyzeCommitQuality(String owner, String repo, RepoAnalysis analysis) {

        List<JsonNode> commits = gitHubService.getCommits(owner, repo);

        CommitQuality commitQuality = commitService.analyzeCommits(commits, analysis);

        // Save to database
        commitQuality = commitQualityRepository.save(commitQuality);

        return commitQuality;
    }

    // ─── CALCULATE FINAL HEALTH SCORE (0-100) ────────────────────────────
    //
    // Formula:
    //   healthScore = (fileHealthScore * 0.60) + (commitQualityScore * 0.40)
    //
    // fileHealthScore = 100 - averageFileRiskScore
    //   (lower risk = higher health)
    //
    private int calculateHealthScore(List<FileRisk> fileRisks, CommitQuality commitQuality) {

        // ── File health component (60% weight) ──────────────────────
        double avgFileRisk = 0;
        if (!fileRisks.isEmpty()) {
            double totalRisk = 0;
            for (FileRisk fr : fileRisks) {
                totalRisk += fr.getRiskScore();
            }
            avgFileRisk = totalRisk / fileRisks.size();
        }
        // Invert: low risk = high health
        double fileHealthScore = 100 - avgFileRisk;

        // ── Commit quality component (40% weight) ───────────────────
        double commitScore = commitQuality.getQuality_score() != null
                ? commitQuality.getQuality_score() : 50;

        // ── Weighted combination ─────────────────────────────────────
        double healthScore = (fileHealthScore * 0.60) + (commitScore * 0.40);

        // Clamp between 0 and 100
        return (int) Math.round(Math.max(0, Math.min(100, healthScore)));
    }

    // ─── DETERMINE OVERALL RISK LEVEL ────────────────────────────────────
    //
    // Health ≥ 70  → LOW risk    (repo is healthy)
    // Health 40-69 → MEDIUM risk (some concerns)
    // Health < 40  → HIGH risk   (needs attention)
    //
    private RepoAnalysis.RiskLevel determineOverallRisk(int healthScore) {
        if (healthScore >= 70) return RepoAnalysis.RiskLevel.LOW;
        if (healthScore >= 40) return RepoAnalysis.RiskLevel.MEDIUM;
        return RepoAnalysis.RiskLevel.HIGH;
    }
}
