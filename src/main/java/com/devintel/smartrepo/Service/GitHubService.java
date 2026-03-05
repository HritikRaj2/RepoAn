package com.devintel.smartrepo.Service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class GitHubService {

    private final WebClient webClient;

    private static final Map<String, List<String>> LANGUAGE_EXTENSIONS = new HashMap<>();

    static {
        LANGUAGE_EXTENSIONS.put("Java",       Arrays.asList(".java"));
        LANGUAGE_EXTENSIONS.put("Python",     Arrays.asList(".py"));
        LANGUAGE_EXTENSIONS.put("JavaScript", Arrays.asList(".js", ".jsx"));
        LANGUAGE_EXTENSIONS.put("TypeScript", Arrays.asList(".ts", ".tsx"));
        LANGUAGE_EXTENSIONS.put("C++",        Arrays.asList(".cpp", ".cc", ".h", ".hpp"));
        LANGUAGE_EXTENSIONS.put("C#",         Arrays.asList(".cs"));
        LANGUAGE_EXTENSIONS.put("Go",         Arrays.asList(".go"));
        LANGUAGE_EXTENSIONS.put("Kotlin",     Arrays.asList(".kt"));
        LANGUAGE_EXTENSIONS.put("Ruby",       Arrays.asList(".rb"));
        LANGUAGE_EXTENSIONS.put("PHP",        Arrays.asList(".php"));
    }

    public GitHubService(@Value("${github.token:}") String githubToken) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)  // 10 MB buffer limit
                );

        if (githubToken != null && !githubToken.isEmpty()) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }

        this.webClient = builder.build();
    }

    // ─── 1. PARSE OWNER AND REPO FROM URL ───────────────────────────────
    public String[] parseRepoUrl(String repoUrl) {
        repoUrl = repoUrl.trim().replaceAll("/$", "");
        String[] parts = repoUrl.split("/");
        String owner = parts[parts.length - 2];
        String repo  = parts[parts.length - 1];
        return new String[]{owner, repo};
    }

    // ─── 2. GET REPO METADATA (also tells us primary language) ───────────
    public JsonNode getRepoMetadata(String owner, String repo) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    // ─── 3. DETECT PRIMARY LANGUAGE FROM REPO ────────────────────────────
    public String detectLanguage(String owner, String repo) {
        JsonNode metadata = getRepoMetadata(owner, repo);
        if (metadata != null && metadata.has("language")
                && !metadata.get("language").isNull()) {
            return metadata.get("language").asText(); // e.g. "Java", "Python"
        }
        return "Java"; // default fallback
    }

    // ─── 4. GET EXTENSIONS FOR A LANGUAGE ────────────────────────────────
    public List<String> getExtensionsForLanguage(String language) {
        return LANGUAGE_EXTENSIONS.getOrDefault(language,
                Arrays.asList(".java")); // fallback to java
    }

    // ─── 5. GET ALL SOURCE FILES (auto-detects language) ─────────────────
    public List<JsonNode> getSourceFiles(String owner, String repo) {
        // Auto detect language
        String language = detectLanguage(owner, repo);
        List<String> extensions = getExtensionsForLanguage(language);

        return getSourceFilesByExtensions(owner, repo, extensions);
    }

    // ─── 6. GET SOURCE FILES BY SPECIFIC EXTENSIONS ──────────────────────
    public List<JsonNode> getSourceFilesByExtensions(String owner, String repo,
                                                     List<String> extensions) {
        JsonNode tree = webClient.get()
                .uri("/repos/{owner}/{repo}/git/trees/HEAD?recursive=1", owner, repo)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<JsonNode> sourceFiles = new ArrayList<>();
        if (tree != null && tree.has("tree")) {
            for (JsonNode file : tree.get("tree")) {
                String path = file.get("path").asText();
                String type = file.get("type").asText();

                // Skip test files and vendor/node_modules directories
                if (isSkippable(path)) continue;

                if (type.equals("blob")) {
                    for (String ext : extensions) {
                        if (path.endsWith(ext)) {
                            sourceFiles.add(file);
                            break;
                        }
                    }
                }
            }
        }
        return sourceFiles;
    }

    // ─── 7. SKIP TEST/VENDOR/BUILD FILES ─────────────────────────────────
    private boolean isSkippable(String path) {
        String lower = path.toLowerCase();
        return lower.contains("node_modules/")
                || lower.contains("vendor/")
                || lower.contains("target/")
                || lower.contains("build/")
                || lower.contains("dist/")
                || lower.contains(".min.js")
                || lower.contains("test/")
                || lower.contains("tests/");
    }

    // ─── 8. GET FILE CONTENT (decoded from base64) ───────────────────────
    public String getFileContent(String owner, String repo, String sha) {
        JsonNode blob = webClient.get()
                .uri("/repos/{owner}/{repo}/git/blobs/{sha}", owner, repo, sha)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (blob != null && blob.has("content")) {
            String encoded = blob.get("content").asText()
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded);
        }
        return "";
    }

    // ─── 9. GET LAST 100 COMMITS ─────────────────────────────────────────
    public List<JsonNode> getCommits(String owner, String repo) {
        JsonNode commits = webClient.get()
                .uri("/repos/{owner}/{repo}/commits?per_page=100", owner, repo)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<JsonNode> commitList = new ArrayList<>();
        if (commits != null && commits.isArray()) {
            for (JsonNode commit : commits) {
                commitList.add(commit);
            }
        }
        return commitList;
    }

    // ─── 10. CHECK IF LANGUAGE IS SUPPORTED ──────────────────────────────
    public boolean isLanguageSupported(String language) {
        return LANGUAGE_EXTENSIONS.containsKey(language);
    }

    // ─── 11. GET ALL SUPPORTED LANGUAGES ─────────────────────────────────
    public Set<String> getSupportedLanguages() {
        return LANGUAGE_EXTENSIONS.keySet();
    }
}