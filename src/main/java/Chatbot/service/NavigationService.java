package Chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

public class NavigationService {

    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);

    private final ChatClient chatClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String pseApiKey;
    private final String pseCxId;

    // map entry subdomains
    private static final Map<String, String> PORTAL_MAP = Map.ofEntries(
            Map.entry("economy", "https://eap.geostat.ge"),
            Map.entry("prices", "https://kaleidoscope.geostat.ge/"),
            Map.entry("population", "https://census2024.geostat.ge/ka"),
            Map.entry("gis", "https://gis.geostat.ge/geomap/index.html"),
            Map.entry("gender", "https://gender.geostat.ge/gender/index.php"),
            Map.entry("environment", "https://environment.geostat.ge/"),
            Map.entry("regions", "https://regions.geostat.ge/regions/"),
            Map.entry("youth", "https://youth.geostat.ge/index.php?lang=ka"),
            Map.entry("automobile", "https://automobile.geostat.ge/ka/"),
            Map.entry("agriculture", "https://agriculture.geostat.ge/"),
            Map.entry("tourism", "https://tourism.geostat.ge/"),
            Map.entry("disability", "https://disability.geostat.ge/shshm/index.php?lang=ka"),
            Map.entry("fdi", "https://fdi.geostat.ge/"),
            Map.entry("energy", "https://energy.geostat.ge"),
            Map.entry("international", "https://i-rating.geostat.ge/"),
            Map.entry("taxes", "https://mytaxes.geostat.ge/mytaxes/"),
            Map.entry("trade", "https://ex-trade.geostat.ge/"),
            Map.entry("wages", "https://salarium.geostat.ge/")
    );


    private static final Map<String, PortalDescription> PORTAL_DESCRIPTIONS = Map.ofEntries(
            Map.entry("international", new PortalDescription(
                    "საერთაშორისო შედარებები და გლობალური რეიტინგები",
                    "International comparisons and global rankings"
            )),
            Map.entry("gis", new PortalDescription(
                    "გეოგრაფიული ინფორმაციული სისტემა და რუქები",
                    "Geographic Information System and maps"
            )),
            Map.entry("wages", new PortalDescription(
                    "ხელფასების კალკულატორი და შრომის ბაზრის ანალიზი",
                    "Salary calculator and labor market analysis"
            ))
    );

    private static class PortalDescription {
        String georgian;
        String english;

        PortalDescription(String georgian, String english) {
            this.georgian = georgian;
            this.english = english;
        }
    }

    private static class SearchResult {
        String title;
        String link;
        String snippet;
        int score;

        SearchResult(String title, String link, String snippet) {
            this.title = title;
            this.link = link;
            this.snippet = snippet;
            this.score = calculateRelevanceScore();
        }

        private int calculateRelevanceScore() {
            if (!link.contains("geostat.ge")) {
                return -10000;
            }

            int score = 100;

            // Boosts for good content
            if (link.contains("/modules/categories/")) score += 30;
            if (link.contains("/page/")) score += 30;
            if (link.matches("https://[a-zA-Z0-9.-]+\\.geostat\\.ge.*")) score += 40;
            if (link.matches("https://www\\.geostat\\.ge/[a-z]{2}/[a-zA-Z-]+/?$")) score += 35;
            if (link.length() < 80) score += 20;

            // Penalties for dated/report content
            if (link.contains(".pdf")) score -= 70;
            if (link.contains("/media/") && link.contains(".pdf")) score -= 40;
            if (title.matches(".*202[0-4].*")) score -= 25;
            if (title.matches(".*(იანვარი|თებერვალი|მარტი|აპრილი|მაისი|ივნისი|ივლისი|აგვისტო|სექტემბერი|ოქტომბერი|ნოემბერი|დეკემბერი).*"))
                score -= 30;
            if (title.matches(".*(January|February|March|April|May|June|July|August|September|October|November|December).*"))
                score -= 30;

            return score;
        }
    }

    public NavigationService(ChatClient chatClient,
                             WebClient webClient,
                             ObjectMapper objectMapper,
                             String pseApiKey,
                             String pseCxId) {
        this.chatClient = chatClient;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.pseApiKey = pseApiKey;
        this.pseCxId = pseCxId;
    }


    public String handleNavigation(String userMessage, QueryPlan plan) {
        log.info("🧭 Handling navigation for topic: {}", plan.topic);

        //  PSE search
        String pseResults = runPseSearch(plan.searchQueries);
        boolean hasResults = !isNoResult(pseResults);

        log.info("🔍 PSE search results: {}", hasResults ? "found" : "none");


        if (!hasResults && PORTAL_MAP.get(plan.topic) == null) {
            log.warn("⚠️ No results and no portal for topic: {}", plan.topic);
            return buildNoResultsMessage(plan.language);
        }


        return buildIntelligentResponse(userMessage, plan, pseResults, hasResults);
    }


    private String buildIntelligentResponse(String userQuestion, QueryPlan plan, String pseResults, boolean hasResults) {
        boolean isGeorgian = "ka".equals(plan.language);
        StringBuilder response = new StringBuilder();


        String portalLink = PORTAL_MAP.get(plan.topic);
        if (portalLink != null) {
            log.info(" Adding portal recommendation: {}", plan.topic);


            response.append(isGeorgian
                    ? "📊 **რეკომენდებული პორტალი**: "
                    : "📊 **Recommended portal**: ");
            response.append(plan.topic.toUpperCase()).append("\n\n");

            response.append(portalLink).append("\n\n");


            PortalDescription desc = PORTAL_DESCRIPTIONS.get(plan.topic);
            if (desc != null) {
                response.append(isGeorgian ? desc.georgian : desc.english).append("\n\n");
            } else {
                response.append(isGeorgian
                                ? "ინტერაქტიული ვიზუალიზაცია და მონაცემები."
                                : "Interactive visualisations and data.")
                        .append("\n\n");
            }
        }


        if (hasResults) {
            log.info("📊 Analyzing PSE results with Claude");
            String analysis = analyzeResultsWithClaude(userQuestion, pseResults, plan.language);
            if (analysis != null && !analysis.isBlank()) {
                response.append(analysis.trim());
            }
        }

        String finalResponse = response.toString().trim();
        log.info(" Built response with {} chars", finalResponse.length());
        return finalResponse;
    }

    private String analyzeResultsWithClaude(String userQuestion, String pseResults, String language) {
        boolean isGeorgian = "ka".equals(language);

        String heading = isGeorgian
                ? "📄 **შესაბამისი გვერდი:**"
                : "📄 **Relevant Page:**";

        String langLabel = isGeorgian ? "Georgian language" : "English language";

        String analysisPrompt = String.format("""
                You are GeoStat Navigator. Your job is to find the MOST RELEVANT page from search results.
                
                ══════════════════════════════════════════════════════════
                USER'S QUESTION:
                ══════════════════════════════════════════════════════════
                %s
                
                ══════════════════════════════════════════════════════════
                SEARCH RESULTS FROM GEOSTAT WEBSITE:
                ══════════════════════════════════════════════════════════
                %s
                
                ══════════════════════════════════════════════════════════
                YOUR TASK:
                ══════════════════════════════════════════════════════════
                
                1. Read ALL search results carefully
                2. Identify the SINGLE MOST RELEVANT page
                3. Consider:
                   ✓ Which title best matches the user's question?
                   ✓ Which description is most relevant?
                   ✓ Prefer main category pages over news articles
                   ✓ Prefer pages with ongoing data over old PDFs
                   ✓ Prefer interactive portals over static pages
                
                ══════════════════════════════════════════════════════════
                CRITICAL OUTPUT FORMAT - FOLLOW EXACTLY:
                ══════════════════════════════════════════════════════════
                
                Your COMPLETE response must be in %s and look EXACTLY like this:
                
                Line 1: %s
                Line 2: [URL only, nothing else]
                Line 3: [blank line]
                Lines 4-5: [1-2 sentences explaining what user will find on this page]
                
                ══════════════════════════════════════════════════════════
                EXAMPLE OUTPUT:
                ══════════════════════════════════════════════════════════
                
                %s
                https://eap.geostat.ge
                
                ამ გვერდზე ნახავთ ეკონომიკური აქტივობის ვიზუალიზაციას და მშპ-ს დეტალურ მონაცემებს. ინტერაქტიული გრაფიკები საშუალებას გაძლევთ შეადაროთ სხვადასხვა პერიოდები.
                
                ══════════════════════════════════════════════════════════
                CRITICAL RULES:
                ══════════════════════════════════════════════════════════
                
                - DO NOT add any text before the heading
                - DO NOT say "Here is..." or "The answer is..." or "I found..."
                - DO NOT add any text after your explanation
                - Start IMMEDIATELY with: %s
                - Second line MUST be URL only (no "URL:", no extra text)
                - Keep explanation brief: 1-2 sentences maximum
                
                ══════════════════════════════════════════════════════════
                
                BEGIN YOUR RESPONSE NOW:
                """,
                userQuestion,
                pseResults,
                langLabel,
                heading,
                heading,
                heading
        );

        try {
            log.info(" Calling Claude for result analysis");
            String analysis = chatClient.prompt()
                    .user(analysisPrompt)
                    .call()
                    .content();

            if (analysis == null) {
                throw new IllegalStateException("Claude returned null analysis");
            }

            log.info(" Claude analysis completed: {} chars", analysis.length());

            // Clean up any potential leading/trailing whitespace
            return analysis.trim();

        } catch (Exception e) {
            log.error(" Claude analysis failed", e);

            // Fallback: return first valid URL
            String[] lines = pseResults.split("\n");
            for (String line : lines) {
                if (line.startsWith("URL: ")) {
                    String url = line.substring(5).trim();
                    log.info("🔄 Using fallback URL: {}", url);
                    return heading + "\n" + url;
                }
            }

            log.warn("️No fallback URL found");
            return heading + "\n" + pseResults.split("\n")[0];
        }
    }

    // PSE SEARCH ENGINE
    private String runPseSearch(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            log.warn("⚠️ No search queries provided");
            return "No results found";
        }

        log.info("🔍 Starting PSE search with {} queries", queries.size());

        // Try each planned query
        for (int i = 0; i < queries.size(); i++) {
            String q = "site:geostat.ge " + queries.get(i);
            log.info("🔎 Attempt #{}: '{}'", i + 1, q);

            String result = formatPseResults(searchGeostat(q));
            if (!isNoResult(result)) {
                log.info("✅ Found results on attempt #{}", i + 1);
                return result;
            }
        }

        // Fallback: try first word only
        if (!queries.isEmpty()) {
            String firstWord = queries.get(0).split("\\s+")[0];
            if (firstWord.length() > 2) {
                String q = "site:geostat.ge " + firstWord;
                log.info("🔄 Fallback (first word): '{}'", q);

                String result = formatPseResults(searchGeostat(q));
                if (!isNoResult(result)) {
                    log.info(" Fallback success");
                    return result;
                }
            }
        }

        log.warn(" No results found after all attempts");
        return "No results found";
    }

    private String searchGeostat(String query) {
        try {
            log.info("🌐 Calling Google PSE API");
            String result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("key", pseApiKey)
                            .queryParam("cx", pseCxId)
                            .queryParam("q", query)
                            .queryParam("num", 10)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            boolean hasResults = result != null && result.contains("\"items\"");
            log.info("📡 PSE API response received: {}", hasResults ? "has items" : "empty");

            return result != null ? result : "{}";
        } catch (Exception e) {
            log.error("❌ PSE API Error: {}", e.getMessage());
            return "{}";
        }
    }

    private String formatPseResults(String json) {
        try {
            JsonNode items = objectMapper.readTree(json).get("items");
            if (items == null || items.isEmpty()) {
                return "No results found";
            }

            log.info("📦 Processing {} search results", items.size());

            List<SearchResult> results = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                String title = item.path("title").asText();
                String link = item.path("link").asText();
                String snippet = item.path("snippet").asText();

                // Truncate long snippets for better Claude analysis
                if (snippet.length() > 250) {
                    snippet = snippet.substring(0, 247) + "...";
                }

                SearchResult result = new SearchResult(title, link, snippet);

                if (result.score > 0) {
                    results.add(result);
                    log.debug("  ✓ Added result with score {}: {}", result.score, title);
                }
            }

            if (results.isEmpty()) {
                log.info("⚠️ All results filtered out (low scores)");
                return "No results found";
            }

            // Return TOP 5 results WITH SNIPPETS for Claude to analyze
            List<SearchResult> topResults = results.stream()
                    .sorted((a, b) -> Integer.compare(b.score, a.score))
                    .limit(5)
                    .collect(Collectors.toList());

            log.info("📊 Returning top {} results", topResults.size());

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < topResults.size(); i++) {
                SearchResult result = topResults.get(i);
                sb.append(String.format("Result #%d (score: %d):\n", i + 1, result.score));
                sb.append(String.format("Title: %s\n", result.title));
                sb.append(String.format("URL: %s\n", result.link));
                sb.append(String.format("Description: %s\n\n", result.snippet));
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("❌ Parse error", e);
            return "No results found";
        }
    }

    private String buildNoResultsMessage(String language) {
        boolean isGeorgian = "ka".equals(language);
        log.info("ℹ️ Building 'no results' message in {}", language);
        return isGeorgian
                ? "ვერ მოიძებნა შესაბამისი გვერდები.\n\nსცადეთ:\n- მთავარი კატეგორიები: https://www.geostat.ge/ka/modules/categories\n- მონაცემთა პორტალები: https://www.geostat.ge/ka/page/data-portals"
                : "Couldn't find relevant pages.\n\nTry:\n- Main categories: https://www.geostat.ge/en/modules/categories\n- Data portals: https://www.geostat.ge/en/page/data-portals";
    }

    private boolean isNoResult(String results) {
        return results == null || results.contains("No results found");
    }
}
