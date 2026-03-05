package com.devintel.smartrepo.Controller;

import com.devintel.smartrepo.Entity.CommitQuality;
import com.devintel.smartrepo.Entity.FileRisk;
import com.devintel.smartrepo.Entity.RepoAnalysis;
import com.devintel.smartrepo.Repository.CommitQualityRepository;
import com.devintel.smartrepo.Repository.FileRiskRepository;
import com.devintel.smartrepo.Repository.RepoAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final RepoAnalysisRepository repoAnalysisRepository;
    private final FileRiskRepository fileRiskRepository;
    private final CommitQualityRepository commitQualityRepository;

    // ─── 1. FULL DETAILED REPORT ─────────────────────────────────────────
    // GET /api/report/{analysisId}
    // Returns: overview + all file risks + commit quality
    @GetMapping("/{analysisId}")
    public ResponseEntity<Map<String, Object>> getFullReport(@PathVariable Integer analysisId) {

        // Find analysis
        RepoAnalysis analysis = repoAnalysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Analysis not found",
                    "analysisId", analysisId
            ));
        }

        // Get file risks (sorted by risk score descending — riskiest files first)
        List<FileRisk> fileRisks = fileRiskRepository.findByAnalysisOrderByRiskScoreDesc(analysis);

        // Get commit quality
        CommitQuality commitQuality = commitQualityRepository.findByAnalysis(analysis).orElse(null);

        // Build response
        Map<String, Object> response = new HashMap<>();

        // ── Overview section ─────────────────────────────────────────
        Map<String, Object> overview = new HashMap<>();
        overview.put("id", analysis.getId());
        overview.put("repoUrl", analysis.getRepoUrl());
        overview.put("healthScore", analysis.getHealthScore());
        overview.put("riskLevel", analysis.getRiskLevel());
        overview.put("aiSummary", analysis.getAiSummary());
        response.put("overview", overview);

        // ── File risks section ───────────────────────────────────────
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

        Map<String, Object> fileSection = new HashMap<>();
        fileSection.put("totalFiles", fileList.size());
        fileSection.put("files", fileList);
        response.put("fileAnalysis", fileSection);

        // ── Commit quality section ───────────────────────────────────
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

    // ─── 2. FILE RISKS ONLY ──────────────────────────────────────────────
    // GET /api/report/{analysisId}/files
    // Returns: just the file-level risk breakdown
    @GetMapping("/{analysisId}/files")
    public ResponseEntity<Map<String, Object>> getFileRisks(@PathVariable Integer analysisId) {

        RepoAnalysis analysis = repoAnalysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Analysis not found"
            ));
        }

        List<FileRisk> fileRisks = fileRiskRepository.findByAnalysisOrderByRiskScoreDesc(analysis);

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

        // Count by risk level
        long highCount   = fileRisks.stream().filter(f -> "HIGH".equals(f.getRiskLevel())).count();
        long mediumCount = fileRisks.stream().filter(f -> "MEDIUM".equals(f.getRiskLevel())).count();
        long lowCount    = fileRisks.stream().filter(f -> "LOW".equals(f.getRiskLevel())).count();

        Map<String, Object> response = new HashMap<>();
        response.put("totalFiles", fileList.size());
        response.put("highRiskFiles", highCount);
        response.put("mediumRiskFiles", mediumCount);
        response.put("lowRiskFiles", lowCount);
        response.put("files", fileList);

        return ResponseEntity.ok(response);
    }

    // ─── 3. COMMIT QUALITY ONLY ──────────────────────────────────────────
    // GET /api/report/{analysisId}/commits
    // Returns: just the commit quality analysis
    @GetMapping("/{analysisId}/commits")
    public ResponseEntity<Map<String, Object>> getCommitQuality(@PathVariable Integer analysisId) {

        RepoAnalysis analysis = repoAnalysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Analysis not found"
            ));
        }

        CommitQuality commitQuality = commitQualityRepository.findByAnalysis(analysis).orElse(null);
        if (commitQuality == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Commit analysis not found for this report"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("totalCommits", commitQuality.getTotal_commits());
        response.put("goodMessages", commitQuality.getGood_messages());
        response.put("badMessages", commitQuality.getBad_messages());
        response.put("avgCommitSize", commitQuality.getAvg_commit_size());
        response.put("qualityScore", commitQuality.getQuality_score());

        // Quality rating label
        int score = commitQuality.getQuality_score() != null ? commitQuality.getQuality_score() : 0;
        if (score >= 80) {
            response.put("rating", "EXCELLENT");
        } else if (score >= 60) {
            response.put("rating", "GOOD");
        } else if (score >= 40) {
            response.put("rating", "FAIR");
        } else {
            response.put("rating", "POOR");
        }

        return ResponseEntity.ok(response);
    }
}