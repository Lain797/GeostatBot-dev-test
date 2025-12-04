package Chatbot.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class QuestionClassifier {

    private static final Logger log = LoggerFactory.getLogger(QuestionClassifier.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;


    private static final String CLASSIFICATION_PROMPT = """
            You are GeoStat Assistant. Analyze the user's question and classify it into the appropriate intent and topic.
            
            ══════════════════════════════════════════════════════════
            QUESTION TYPES (INTENT):
            ══════════════════════════════════════════════════════════
            
            1. "navigation" - User wants to FIND specific data/pages on GeoStat website
               Triggers: "where is", "show me", "find", "how to find", "სად არის", "სად ვნახო"
               Examples: 
               - "where is unemployment data?"
               - "სად ვნახო ინფლაციის მონაცემები?"
               - "show me GDP statistics"
               - "როგორია უმუშევრობის დონე საქართველოში?" (wants to FIND the data)
            
            2. "general_knowledge" - User asks for EXPLANATION of concepts/definitions
               Triggers: "what is", "რა არის", "how is calculated", "როგორ გამოითვლება", "explain"
               Examples:
               - "what is inflation?" (wants definition)
               - "რა არის მშპ?" (wants explanation)
               - "how is CPI calculated?" (wants methodology)
               BUT: "what is the inflation rate in Georgia?" → navigation (wants data, not definition)
            
            3. "small_talk" - Greetings, thanks, or completely off-topic
               Examples: "hello", "გამარჯობა", "thanks", "who are you?", "tell me a joke"
            
            ══════════════════════════════════════════════════════════
            CRITICAL DISAMBIGUATION RULES:
            ══════════════════════════════════════════════════════════
            
            IF question asks "რა არის X?" or "what is X?" → Check context:
              - If X is a CONCEPT (inflation, GDP, CPI) → general_knowledge
              - If X is a STATISTIC for a country (inflation in Georgia) → navigation
            
            IF question asks about CURRENT/RECENT data → ALWAYS navigation
              Examples: "current unemployment", "latest GDP", "2024 inflation" → navigation
            
            IF question mentions SPECIFIC COUNTRY/REGION → navigation
              Examples: "Georgia unemployment", "Tbilisi population" → navigation
            
            IF unclear → Default to "navigation" (safer to show data)
            
            ══════════════════════════════════════════════════════════
            TOPIC CLASSIFICATION (for navigation):
            ══════════════════════════════════════════════════════════
            
            ALWAYS try to match to a specific portal topic FIRST before using "other":
            
            - economy → GDP, მშპ, economic growth, ეკონომიკა, production, business, economic indicators, macroeconomic
            - prices → inflation, CPI, ინფლაცია, ფასები, price index, consumer prices, cost of living, deflation
            - population → census, აღწერა, დემოგრაფია, demographics, migration, births, deaths, მოსახლეობა, residents
            - environment → ecology, გარემო, nature, pollution, climate, ეკოლოგია, emissions, waste
            - energy → electricity, ენერგია, fuel, power, renewable, ელექტროენერგია, gas, oil
            - tourism → visitors, ტურიზმ, hotels, travel, attractions, tourists, hospitality
            - trade → export, import, ვაჭრობა, foreign trade, balance, საგარეო ვაჭრობა, commerce
            - agriculture → farming, სოფლის მეურნეობა, crops, livestock, rural, აგრარული, harvest
            - gender → gender statistics, გენდერი, women, men, equality, gender gap, female, male, ქალები, მამაკაცები
            - regions → municipalities, რეგიონები, territorial, regional, local, მუნიციპალიტეტები, districts
            - youth → young people, ახალგაზრდები, children, teenagers, kids, ბავშვები, adolescents
            - automobile → cars, ავტომობილები, vehicles, transport, auto, მანქანები, automotive
            - wages → salary, ხელფასები, compensation, earnings, income, შრომის ანაზღაურება, pay
            - taxes → taxation, გადასახადები, revenue, fiscal, tax, საგადასახადო, duties
            - fdi → foreign investment, უცხოური ინვესტიციები, capital, investors, პირდაპირი ინვესტიციები, FDI
            - gis → geographic, გეოგრაფიული, maps, რუქები, spatial, cartography, გის, mapping
            - disability → disabled persons, შშმ, accessibility, handicap, შეზღუდული შესაძლებლობები, special needs
            - international → international comparison, საერთაშორისო შედარება, country comparison, global ranking, comparing countries, world statistics, რეიტინგი, ქვეყნების შედარება, benchmarking
            - other → ONLY if none above match: organizational (structure, სტრუქტურა, contact, კონტაქტი, departments, დეპარტამენტები, projects, პროექტები, about geostat, vacancies)
            
            ══════════════════════════════════════════════════════════
            SEARCH QUERY EXTRACTION (for navigation only):
            ══════════════════════════════════════════════════════════
            
            Extract 2-3 SHORT, focused keywords:
            
            REMOVE filler words:
            - Georgian: "სად არის", "როგორ ვნახო", "მინდა ვიცოდე", "გთხოვთ"
            - English: "where", "how to find", "show me", "I want to know", "please"
            
            KEEP core terms:
            - Nouns: "ინფლაცია", "unemployment", "GDP"
            - Adjectives: "foreign", "regional", "annual"
            - Specific terms: "2024", "Tbilisi", "export"
            
            Query order:
            1. First query: user's language (main keywords)
            2. Second: English translation or Georgian equivalent
            3. Third: alternative/broader term
            
            ══════════════════════════════════════════════════════════
            EXAMPLES - Study these carefully:
            ══════════════════════════════════════════════════════════
            
            NAVIGATION EXAMPLES:
            
            Input: "საერთაშორისო შედარება სად ვნახო?"
            Output: {"language":"ka", "intent":"navigation", "topic":"international", "searchQueries":["საერთაშორისო შედარება","international comparison","global ranking"]}
            
            Input: "compare Georgia with other countries"
            Output: {"language":"en", "intent":"navigation", "topic":"international", "searchQueries":["international comparison","country comparison","საერთაშორისო"]}
            
            Input: "ინფლაცია საქართველოში 2024?"
            Output: {"language":"ka", "intent":"navigation", "topic":"prices", "searchQueries":["ინფლაცია","inflation Georgia","consumer prices"]}
            
            Input: "current unemployment rate"
            Output: {"language":"en", "intent":"navigation", "topic":"economy", "searchQueries":["unemployment rate","უმუშევრობა","labor market"]}
            
            Input: "რუქები და გეოგრაფიული მონაცემები"
            Output: {"language":"ka", "intent":"navigation", "topic":"gis", "searchQueries":["რუქები","maps geographic","gis"]}
            
            Input: "ხელფასების კალკულატორი"
            Output: {"language":"ka", "intent":"navigation", "topic":"wages", "searchQueries":["ხელფასები","salary calculator","wages"]}
            
            Input: "gender statistics for Georgia"
            Output: {"language":"en", "intent":"navigation", "topic":"gender", "searchQueries":["gender statistics","გენდერული","women men"]}
            
            Input: "IT department structure"
            Output: {"language":"en", "intent":"navigation", "topic":"other", "searchQueries":["it department","information technology","structure"]}
            
            GENERAL KNOWLEDGE EXAMPLES:
            
            Input: "რა არის ინფლაცია?"
            Output: {"language":"ka", "intent":"general_knowledge", "topic":"prices", "searchQueries":[]}
            
            Input: "what is GDP?"
            Output: {"language":"en", "intent":"general_knowledge", "topic":"economy", "searchQueries":[]}
            
            Input: "how is CPI calculated?"
            Output: {"language":"en", "intent":"general_knowledge", "topic":"prices", "searchQueries":[]}
            
            SMALL TALK EXAMPLES:
            
            Input: "hello"
            Output: {"language":"en", "intent":"small_talk", "topic":"other", "searchQueries":[]}
            
            Input: "გამარჯობა"
            Output: {"language":"ka", "intent":"small_talk", "topic":"other", "searchQueries":[]}
            
            Input: "thanks for help"
            Output: {"language":"en", "intent":"small_talk", "topic":"other", "searchQueries":[]}
            
            ══════════════════════════════════════════════════════════
            CRITICAL OUTPUT REQUIREMENTS:
            ══════════════════════════════════════════════════════════
            
            Return ONLY valid JSON. No markdown code blocks, no explanations, no preamble.
            
            Your ENTIRE response must be EXACTLY this format:
            {"language":"ka", "intent":"navigation", "topic":"economy", "searchQueries":["keyword1","keyword2","keyword3"]}
            
            DO NOT include:
            - ```json or ``` markers
            - "Here is the classification:"
            - Any text before or after the JSON
            
            Your response must START with { and END with }
            
            ══════════════════════════════════════════════════════════
            
            User Input: %s
            
            JSON Response:
            """;

    public QuestionClassifier(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public QueryPlan classify(String userMessage) {
        try {
            String promptText = String.format(CLASSIFICATION_PROMPT, userMessage);
            log.info(" Classification prompt length: {} chars", promptText.length());

            String json = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();

            if (json == null) {
                throw new IllegalStateException("LLM returned null classification");
            }

            log.info(" Raw classification response: {}",
                    json.substring(0, Math.min(json.length(), 200)));

            return parseJsonToPlan(json, userMessage);
        } catch (Exception e) {
            log.error(" Classification failed", e);
            QueryPlan fallback = new QueryPlan();
            fallback.language = LanguageUtils.detectLanguage(userMessage);
            fallback.intent = "navigation";
            fallback.topic = "other";
            fallback.searchQueries = List.of(userMessage);
            normalizePlan(fallback, userMessage);
            log.info(" Using fallback plan: {}", fallback.topic);
            return fallback;
        }
    }

    private QueryPlan parseJsonToPlan(String json, String originalMsg) {
        try {

            if (json.contains("```")) {
                json = json.replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*", "")
                        .trim();
            }


            int startIdx = json.indexOf('{');
            int endIdx = json.lastIndexOf('}');

            if (startIdx >= 0 && endIdx > startIdx) {
                json = json.substring(startIdx, endIdx + 1);
            }

            log.info(" Cleaned JSON: {}", json);

            QueryPlan plan = objectMapper.readValue(json, QueryPlan.class);


            normalizePlan(plan, originalMsg);

            return plan;
        } catch (Exception e) {
            log.error(" JSON Parse Error: {}", json, e);
            QueryPlan fallback = new QueryPlan();
            fallback.language = LanguageUtils.detectLanguage(originalMsg);
            fallback.intent = "navigation";
            fallback.topic = "other";
            fallback.searchQueries = List.of(originalMsg);
            normalizePlan(fallback, originalMsg);
            return fallback;
        }
    }

    /**
     * Normalize classifier output:
     * - trim + lowercase intent/topic
     * - default language, intent, topic
     * - ensure navigation has at least one search query
     */
    private void normalizePlan(QueryPlan plan, String originalMsg) {
        if (plan == null) return;

        // Language fallback
        if (plan.language == null || plan.language.isBlank()) {
            plan.language = LanguageUtils.detectLanguage(originalMsg);
        }


        if (plan.intent == null || plan.intent.isBlank()) {
            plan.intent = "navigation";
        } else {
            plan.intent = plan.intent.trim().toLowerCase(Locale.ROOT);
        }

        if (plan.topic == null || plan.topic.isBlank()) {
            plan.topic = "other";
        } else {
            plan.topic = plan.topic.trim().toLowerCase(Locale.ROOT);
        }

        if (plan.searchQueries == null) {
            plan.searchQueries = new ArrayList<>();
        }
        if ("navigation".equals(plan.intent) && plan.searchQueries.isEmpty()) {
            String[] words = originalMsg.split("\\s+");
            String fallbackQuery = Arrays.stream(words)
                    .limit(3)
                    .collect(Collectors.joining(" "));
            plan.searchQueries = List.of(fallbackQuery);
            log.info("🔄 Added fallback search query: {}", fallbackQuery);
        }
    }
}
