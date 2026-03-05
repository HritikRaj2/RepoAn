package com.devintel.smartrepo.Service;

import com.devintel.smartrepo.Entity.CommitQuality;
import com.devintel.smartrepo.Entity.RepoAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CommitService {

    // ─── BAD COMMIT MESSAGE PATTERNS ─────────────────────────────────────
    // Single-word lazy messages that tell nothing about the change
    private static final Set<String> BAD_KEYWORDS = new HashSet<>(Arrays.asList(
            "fix", "fixed", "fixes", "update", "updated", "updates",
            "wip", "minor", "test", "testing", "temp", "tmp",
            "asdf", "asd", "stuff", "changes", "change", "changed",
            "commit", "push", "save", "done", "oops", "typo",
            "bug", "debug", "refactor", "cleanup", "clean",
            "initial", "init", "first", "start", "begin",
            "misc", "todo", "hack", "hotfix", "revert"
    ));

    // ─── MAIN METHOD: ANALYZE COMMITS ────────────────────────────────────
    public CommitQuality analyzeCommits(List<JsonNode> commits, RepoAnalysis analysis) {

        int totalCommits  = commits.size();
        int goodMessages  = 0;
        int badMessages   = 0;
        int totalSize     = 0;

        for (JsonNode commit : commits) {
            // Extract commit message
            String message = "";
            if (commit.has("commit") && commit.get("commit").has("message")) {
                message = commit.get("commit").get("message").asText().trim();
            }

            // Classify message
            if (isGoodMessage(message)) {
                goodMessages++;
            } else {
                badMessages++;
            }

            // Estimate commit size (from stats if available)
            if (commit.has("stats")) {
                int additions = commit.get("stats").path("additions").asInt(0);
                int deletions = commit.get("stats").path("deletions").asInt(0);
                totalSize += (additions + deletions);
            }
        }

        // Calculate quality score (0-100)
        int qualityScore = 0;
        if (totalCommits > 0) {
            qualityScore = (int) Math.round(((double) goodMessages / totalCommits) * 100);
        }

        // Average commit size
        int avgCommitSize = 0;
        if (totalCommits > 0) {
            avgCommitSize = totalSize / totalCommits;
        }

        // Build entity
        CommitQuality commitQuality = new CommitQuality();
        commitQuality.setAnalysis(analysis);
        commitQuality.setTotal_commits(totalCommits);
        commitQuality.setGood_messages(goodMessages);
        commitQuality.setBad_messages(badMessages);
        commitQuality.setAvg_commit_size(avgCommitSize);
        commitQuality.setQuality_score(qualityScore);

        return commitQuality;
    }

    // ─── IS THIS A GOOD COMMIT MESSAGE? ──────────────────────────────────
    // Good = descriptive, >10 chars, not a single lazy word
    public boolean isGoodMessage(String message) {
        if (message == null || message.isEmpty()) return false;

        // Take only first line (before any newline)
        String firstLine = message.split("\n")[0].trim();

        // Rule 1: Too short (less than 10 characters)
        if (firstLine.length() < 10) return false;

        // Rule 2: Single word only
        String[] words = firstLine.split("\\s+");
        if (words.length <= 1) return false;

        // Rule 3: First word (lowercased) is a known bad keyword
        String firstWord = words[0].toLowerCase().replaceAll("[^a-z]", "");
        if (BAD_KEYWORDS.contains(firstWord) && words.length <= 3) return false;

        // Rule 4: Entire message (lowercased) matches a bad keyword exactly
        String lowerFull = firstLine.toLowerCase().replaceAll("[^a-z\\s]", "").trim();
        if (BAD_KEYWORDS.contains(lowerFull)) return false;

        // Rule 5: Contains at least 2 meaningful words
        int meaningfulWords = 0;
        for (String word : words) {
            if (word.length() > 2) meaningfulWords++;
        }
        if (meaningfulWords < 2) return false;

        // Passed all checks → good message
        return true;
    }
}
