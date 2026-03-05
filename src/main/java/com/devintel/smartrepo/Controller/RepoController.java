package com.devintel.smartrepo.Controller;

import com.devintel.smartrepo.Entity.RepoAnalysis;
import com.devintel.smartrepo.Entity.User;
import com.devintel.smartrepo.Repository.RepoAnalysisRepository;
import com.devintel.smartrepo.Repository.UserRepository;
import com.devintel.smartrepo.Service.RepoAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/repo")
@RequiredArgsConstructor
@Slf4j
public class RepoController {

    private final RepoAnalyzerService repoAnalyzerService;
    private final RepoAnalysisRepository repoAnalysisRepository;
    private final UserRepository userRepository;

    // ─── 1. TRIGGER ANALYSIS ─────────────────────────────────────────────
    // POST /api/repo/analyze
    // Header: Authorization: Bearer <jwt_token>
    // Body: { "repoUrl": "https://github.com/owner/repo" }
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeRepo(
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        String repoUrl = request.get("repoUrl");

        // Validation
        if (repoUrl == null || repoUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "repoUrl is required"
            ));
        }

        // Validate URL format
        if (!repoUrl.matches("https?://github\\.com/[^/]+/[^/]+.*")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid GitHub URL. Expected format: https://github.com/owner/repo"
            ));
        }

        // Get logged-in user
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "User not found"
            ));
        }

        // Kick off async analysis
        CompletableFuture<RepoAnalysis> future = repoAnalyzerService.analyzeRepo(repoUrl, user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Analysis started! It may take 1-2 minutes.");
        response.put("repoUrl", repoUrl);
        response.put("status", "PROCESSING");

        return ResponseEntity.accepted().body(response);
    }

    // ─── 2. GET ANALYSIS RESULT BY REPO URL ──────────────────────────────
    // GET /api/repo/result?repoUrl=https://github.com/owner/repo
    // Header: Authorization: Bearer <jwt_token>
    @GetMapping("/result")
    public ResponseEntity<Map<String, Object>> getResult(@RequestParam String repoUrl) {

        RepoAnalysis analysis = repoAnalysisRepository
                .findTopByRepoUrlOrderByIdDesc(repoUrl)
                .orElse(null);

        if (analysis == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "No analysis found for this URL",
                    "repoUrl", repoUrl
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", analysis.getId());
        response.put("repoUrl", analysis.getRepoUrl());
        response.put("healthScore", analysis.getHealthScore());
        response.put("riskLevel", analysis.getRiskLevel());
        response.put("aiSummary", analysis.getAiSummary());

        // Determine status
        if (analysis.getHealthScore() == 0 && analysis.getAiSummary() == null) {
            response.put("status", "PROCESSING");
        } else {
            response.put("status", "COMPLETE");
        }

        return ResponseEntity.ok(response);
    }

    // ─── 3. GET ALL ANALYSES FOR LOGGED-IN USER ──────────────────────────
    // GET /api/repo/my-reports
    // Header: Authorization: Bearer <jwt_token>
    @GetMapping("/my-reports")
    public ResponseEntity<Map<String, Object>> getMyReports(Authentication authentication) {

        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "User not found"
            ));
        }

        List<RepoAnalysis> reports = repoAnalysisRepository.findByUser(user);

        // Convert to simple maps for clean JSON response
        List<Map<String, Object>> reportList = reports.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("repoUrl", r.getRepoUrl());
            map.put("healthScore", r.getHealthScore());
            map.put("riskLevel", r.getRiskLevel());
            map.put("aiSummary", r.getAiSummary());
            return map;
        }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("count", reportList.size());
        response.put("reports", reportList);

        return ResponseEntity.ok(response);
    }

    // ─── 4. GET ANALYSIS BY ID ───────────────────────────────────────────
    // GET /api/repo/result/5
    // Header: Authorization: Bearer <jwt_token>
    @GetMapping("/result/{id}")
    public ResponseEntity<Map<String, Object>> getResultById(@PathVariable Integer id) {

        RepoAnalysis analysis = repoAnalysisRepository.findById(id).orElse(null);

        if (analysis == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Analysis not found",
                    "id", id
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", analysis.getId());
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
}