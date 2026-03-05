package com.devintel.smartrepo.Service;



import com.devintel.smartrepo.Entity.FileRisk;
import com.devintel.smartrepo.Entity.RepoAnalysis;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FileParserService {

    // ─── SECURITY SMELL PATTERNS ─────────────────────────────────────────
    private static final Pattern[] SECURITY_PATTERNS = {
            // Hardcoded passwords / secrets
            Pattern.compile("(?i)(password|passwd|pwd|secret|api_key|apikey|token)\\s*=\\s*\"[^\"]+\""),
            // SQL injection risk (string concatenation in queries)
            Pattern.compile("(?i)(execute|executeQuery|executeUpdate)\\s*\\(\\s*\".*\\+"),
            Pattern.compile("(?i)\"\\s*\\+.*\\+\\s*\".*(?:SELECT|INSERT|UPDATE|DELETE|DROP|WHERE)"),
            // Hardcoded IP addresses
            Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"),
            // eval() or Runtime.exec() usage
            Pattern.compile("(?i)(Runtime\\.getRuntime\\(\\)\\.exec|eval\\s*\\()"),
            // Disabled SSL/TLS verification
            Pattern.compile("(?i)(setHostnameVerifier|TrustAllCerts|ALLOW_ALL|disableSsl)"),
            // Hardcoded credentials in URLs
            Pattern.compile("(?i)(https?://)\\w+:\\w+@")
    };

    // ─── COMPLEXITY KEYWORDS ─────────────────────────────────────────────
    // Each keyword adds +1 to cyclomatic complexity
    private static final Pattern COMPLEXITY_PATTERN = Pattern.compile(
            "\\b(if|else if|for|while|do|switch|case|catch|&&|\\|\\|)\\b|\\?\\s*[^:]"
    );

    // ─── MAIN METHOD: ANALYZE A SINGLE FILE ──────────────────────────────
    public FileRisk analyzeFile(String filePath, String fileContent, RepoAnalysis analysis) {

        int linesOfCode    = countLinesOfCode(fileContent);
        int complexity      = calculateComplexity(fileContent);
        int securitySmells  = detectSecuritySmells(fileContent);
        int riskScore       = calculateRiskScore(linesOfCode, complexity, securitySmells);
        String riskLevel    = determineRiskLevel(riskScore);

        FileRisk fileRisk = new FileRisk();
        fileRisk.setAnalysis(analysis);
        fileRisk.setFile_path(filePath);
        fileRisk.setLines_of_code(linesOfCode);
        fileRisk.setComplexity_score(complexity);
        fileRisk.setSecurity_smells(securitySmells);
        fileRisk.setRiskScore(riskScore);
        fileRisk.setRiskLevel(riskLevel);

        return fileRisk;
    }

    // ─── 1. COUNT LINES OF CODE (skip blank lines and comments) ──────────
    public int countLinesOfCode(String content) {
        if (content == null || content.isEmpty()) return 0;

        String[] lines = content.split("\n");
        int count = 0;
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // Handle block comments  /* ... */
            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }

            if (trimmed.startsWith("/*")) {
                inBlockComment = true;
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }

            // Skip empty lines and single-line comments
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                continue;
            }

            count++;
        }
        return count;
    }

    // ─── 2. CALCULATE CYCLOMATIC COMPLEXITY ──────────────────────────────
    public int calculateComplexity(String content) {
        if (content == null || content.isEmpty()) return 1;

        // Start at 1 (base complexity for any method)
        int complexity = 1;

        // Remove string literals to avoid false matches
        String cleaned = content.replaceAll("\"[^\"]*\"", "\"\"");
        // Remove single-line comments
        cleaned = cleaned.replaceAll("//.*", "");
        // Remove block comments
        cleaned = cleaned.replaceAll("/\\*[\\s\\S]*?\\*/", "");

        // Count complexity keywords
        String[] complexityKeywords = {
                "\\bif\\b", "\\belse\\s+if\\b", "\\bfor\\b", "\\bwhile\\b",
                "\\bdo\\b", "\\bswitch\\b", "\\bcase\\b", "\\bcatch\\b",
                "&&", "\\|\\|"
        };

        for (String keyword : complexityKeywords) {
            Pattern pattern = Pattern.compile(keyword);
            Matcher matcher = pattern.matcher(cleaned);
            while (matcher.find()) {
                complexity++;
            }
        }

        // Also count ternary operators  ? :
        Matcher ternary = Pattern.compile("\\?\\s*[^:]").matcher(cleaned);
        while (ternary.find()) {
            complexity++;
        }

        return complexity;
    }

    // ─── 3. DETECT SECURITY SMELLS ───────────────────────────────────────
    public int detectSecuritySmells(String content) {
        if (content == null || content.isEmpty()) return 0;

        int smells = 0;
        for (Pattern pattern : SECURITY_PATTERNS) {
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                smells++;
            }
        }
        return smells;
    }

    // ─── 4. CALCULATE RISK SCORE (0-100) ─────────────────────────────────
    // Formula: (complexity * 0.40) + (securitySmells * 0.35) + (normalizedLOC * 0.25)
    public int calculateRiskScore(int linesOfCode, int complexity, int securitySmells) {

        // Normalize complexity to 0-100 scale (cap at 50 branches)
        double normalizedComplexity = Math.min((complexity / 50.0) * 100, 100);

        // Normalize security smells to 0-100 scale (cap at 10 smells)
        double normalizedSecurity = Math.min((securitySmells / 10.0) * 100, 100);

        // Normalize LOC to 0-100 scale (cap at 500 lines)
        double normalizedLOC = Math.min((linesOfCode / 500.0) * 100, 100);

        // Weighted formula
        double riskScore = (normalizedComplexity * 0.40)
                + (normalizedSecurity * 0.35)
                + (normalizedLOC * 0.25);

        return (int) Math.round(Math.min(riskScore, 100));
    }

    // ─── 5. DETERMINE RISK LEVEL ─────────────────────────────────────────
    public String determineRiskLevel(int riskScore) {
        if (riskScore < 30)  return "LOW";
        if (riskScore <= 60) return "MEDIUM";
        return "HIGH";
    }
}