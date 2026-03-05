package com.devintel.smartrepo.Controller;

import com.devintel.smartrepo.Entity.CommitQuality;
import com.devintel.smartrepo.Entity.FileRisk;
import com.devintel.smartrepo.Entity.RepoAnalysis;
import com.devintel.smartrepo.Repository.CommitQualityRepository;
import com.devintel.smartrepo.Repository.FileRiskRepository;
import com.devintel.smartrepo.Repository.RepoAnalysisRepository;
import com.devintel.smartrepo.Service.RepoAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicController {

    private final RepoAnalyzerService repoAnalyzerService;
    private final RepoAnalysisRepository repoAnalysisRepository;
    private final FileRiskRepository fileRiskRepository;
    private final CommitQualityRepository commitQualityRepository;

    // ─── 1. ANALYZE A REPO (NO LOGIN REQUIRED) ──────────────────────────
    // POST /api/public/analyze
    // Body: { "repoUrl": "https://github.com/owner/repo" }
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeRepo(@RequestBody Map<String, String> request) {

        String repoUrl = request.get("repoUrl");

        // Validation
        if (repoUrl == null || repoUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "repoUrl is required"
            ));
        }

        // Clean URL
        repoUrl = repoUrl.trim().replaceAll("/$", "");

        // Validate URL format
        if (!repoUrl.matches("https?://github\\.com/[^/]+/[^/]+.*")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid GitHub URL. Expected format: https://github.com/owner/repo"
            ));
        }

        // Check if this repo was already analyzed recently (within last analysis)
        RepoAnalysis existing = repoAnalysisRepository
                .findTopByRepoUrlOrderByIdDesc(repoUrl)
                .orElse(null);

        if (existing != null && existing.getHealthScore() > 0) {
            // Return cached result
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Analysis found (cached result)");
            response.put("analysisId", existing.getId());
            response.put("repoUrl", existing.getRepoUrl());
            response.put("healthScore", existing.getHealthScore());
            response.put("riskLevel", existing.getRiskLevel());
            response.put("status", "COMPLETE");
            return ResponseEntity.ok(response);
        }

        // Start new analysis (user = null for public requests)
        CompletableFuture<RepoAnalysis> future = repoAnalyzerService.analyzeRepo(repoUrl, null);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Analysis started! Check back in 1-2 minutes.");
        response.put("repoUrl", repoUrl);
        response.put("status", "PROCESSING");

        return ResponseEntity.accepted().body(response);
    }

    // ─── 2. CHECK RESULT (NO LOGIN REQUIRED) ─────────────────────────────
    // GET /api/public/result?repoUrl=https://github.com/owner/repo
    @GetMapping("/result")
    public ResponseEntity<Map<String, Object>> getResult(@RequestParam String repoUrl) {

        repoUrl = repoUrl.trim().replaceAll("/$", "");

        RepoAnalysis analysis = repoAnalysisRepository
                .findTopByRepoUrlOrderByIdDesc(repoUrl)
                .orElse(null);

        if (analysis == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "No analysis found for this URL. Submit it first.",
                    "repoUrl", repoUrl
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("analysisId", analysis.getId());
        response.put("repoUrl", analysis.getRepoUrl());
        response.put("healthScore", analysis.getHealthScore());
        response.put("riskLevel", analysis.getRiskLevel());
        response.put("aiSummary", analysis.getAiSummary());

        if (analysis.getHealthScore() == 0 && analysis.getAiSummary() == null) {
            response.put("status", "PROCESSING");
        } else {
            response.put("status", "COMPLETE");
        }

        return ResponseEntity.ok(response);
    }

    // ─── 3. FULL REPORT (NO LOGIN REQUIRED) ──────────────────────────────
    // GET /api/public/report/{analysisId}
    @GetMapping("/report/{analysisId}")
    public ResponseEntity<Map<String, Object>> getFullReport(@PathVariable Integer analysisId) {

        RepoAnalysis analysis = repoAnalysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Analysis not found"
            ));
        }

        List<FileRisk> fileRisks = fileRiskRepository.findByAnalysisOrderByRiskScoreDesc(analysis);
        CommitQuality commitQuality = commitQualityRepository.findByAnalysis(analysis).orElse(null);

        // Build response
        Map<String, Object> response = new HashMap<>();

        // Overview
        Map<String, Object> overview = new HashMap<>();
        overview.put("id", analysis.getId());
        overview.put("repoUrl", analysis.getRepoUrl());
        overview.put("healthScore", analysis.getHealthScore());
        overview.put("riskLevel", analysis.getRiskLevel());
        overview.put("aiSummary", analysis.getAiSummary());

        if (analysis.getHealthScore() == 0 && analysis.getAiSummary() == null) {
            overview.put("status", "PROCESSING");
        } else {
            overview.put("status", "COMPLETE");
        }
        response.put("overview", overview);

        // File analysis
        List<Map<String, Object>> fileList = fileRisks.stream().map(f -> {
            Map<String, Object> map = new HashMap<>();
            map.put("filePath", f.getFile_path());
            map.put("linesOfCode", f.getLines_of_code());
            map.put("complexityScore", f.getComplexity_score());
            map.put("securitySmells", f.getSecurity_smells());
            map.put("riskScore", f.getRiskScore());
            map.put("riskLevel", f.getRiskLevel());
            return map;
        }).toList();

        long highCount   = fileRisks.stream().filter(f -> "HIGH".equals(f.getRiskLevel())).count();
        long mediumCount = fileRisks.stream().filter(f -> "MEDIUM".equals(f.getRiskLevel())).count();
        long lowCount    = fileRisks.stream().filter(f -> "LOW".equals(f.getRiskLevel())).count();

        Map<String, Object> fileSection = new HashMap<>();
        fileSection.put("totalFiles", fileList.size());
        fileSection.put("highRiskFiles", highCount);
        fileSection.put("mediumRiskFiles", mediumCount);
        fileSection.put("lowRiskFiles", lowCount);
        fileSection.put("files", fileList);
        response.put("fileAnalysis", fileSection);

        // Commit analysis
        if (commitQuality != null) {
            Map<String, Object> commitSection = new HashMap<>();
            commitSection.put("totalCommits", commitQuality.getTotal_commits());
            commitSection.put("goodMessages", commitQuality.getGood_messages());
            commitSection.put("badMessages", commitQuality.getBad_messages());
            commitSection.put("avgCommitSize", commitQuality.getAvg_commit_size());
            commitSection.put("qualityScore", commitQuality.getQuality_score());
            response.put("commitAnalysis", commitSection);
        }

        return ResponseEntity.ok(response);
    }
}