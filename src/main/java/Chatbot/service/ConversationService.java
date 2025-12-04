package Chatbot.service;

import org.springframework.ai.chat.client.ChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ChatClient chatClient;

    public ConversationService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }


    public String handleGeneralKnowledge(String userMessage, String language) {
        boolean isGeorgian = "ka".equals(language);

        String knowledgePrompt = String.format("""
                You are GeoStat Assistant - an expert on statistics and the National Statistics Office of Georgia.
                
                User's Question: %s
                
                ══════════════════════════════════════════════════════════
                YOUR TASK:
                ══════════════════════════════════════════════════════════
                
                Answer this question clearly and concisely in %s.
                
                ══════════════════════════════════════════════════════════
                CRITICAL RULES - NO EXCEPTIONS:
                ══════════════════════════════════════════════════════════
                
                1. NEVER state specific numbers or statistics about Georgia
                   ❌ Bad: "Inflation in Georgia is 2.5%%"
                   ✅ Good: "Inflation data is available on geostat.ge"
                
                2. NEVER claim to know "current" or "latest" data
                   ❌ Bad: "The current unemployment rate is..."
                   ✅ Good: "You can find current unemployment data at geostat.ge"
                
                3. If question is about statistics concepts (inflation, GDP, CPI, etc.):
                   - Explain the concept in 2-3 sentences
                   - Mention: "დეტალური მონაცემები საქართველოზე ხელმისაწვდომია geostat.ge-ზე"
                     OR "Detailed data about Georgia is available at geostat.ge"
                
                4. If question is clearly NOT about statistics or GeoStat:
                   - Politely redirect: "მე ვარ GeoStat-ის ასისტენტი და ვეხმარები საქართველოს სტატისტიკაში"
                   - Or in English: "I'm GeoStat Assistant and I help with Georgian statistics"
                   - DO NOT try to answer unrelated topics
                
                ══════════════════════════════════════════════════════════
                RESPONSE STRUCTURE (exactly 3-4 sentences):
                ══════════════════════════════════════════════════════════
                
                Sentence 1: Direct answer to the question (concept explanation)
                Sentence 2-3: Key details or context
                Sentence 4: If relevant, mention where to find actual data
                
                ══════════════════════════════════════════════════════════
                URL FORMATTING:
                ══════════════════════════════════════════════════════════
                
                If you mention a URL, put it on its own line with a blank line before it:
                
                Example:
                "ინფლაცია არის ფასების ზრდის ტემპი დროში. ის გამოითვლება სამომხმარებლო ფასების ინდექსით (CPI).
                
                დეტალური მონაცემები საქართველოზე ხელმისაწვდომია:
                https://www.geostat.ge"
                
                ══════════════════════════════════════════════════════════
                
                Your answer in %s (3-4 sentences maximum):
                """,
                userMessage,
                isGeorgian ? "Georgian language" : "English language",
                isGeorgian ? "Georgian language" : "English language"
        );

        try {
            log.info(" Generating knowledge response in {}", language);
            String response = chatClient.prompt()
                    .user(knowledgePrompt)
                    .call()
                    .content();

            if (response == null) {
                throw new IllegalStateException("Knowledge LLM returned null response");
            }

            log.info(" Knowledge response generated: {} chars", response.length());
            return response.trim();
        } catch (Exception e) {
            log.error(" Knowledge response failed", e);
            return isGeorgian
                    ? "ვერ მოხერხდა პასუხის გენერირება. გთხოვთ, სცადოთ თავიდან ან ეწვიოთ www.geostat.ge-ს"
                    : "Unable to generate response. Please try again or visit www.geostat.ge";
        }
    }


    public String handleSmallTalk(String userMessage, String language) {
        boolean isGeorgian = "ka".equals(language);

        String smallTalkPrompt = String.format("""
                You are GeoStat Assistant - a friendly, professional chatbot for Georgia's National Statistics Office.
                
                User said: %s
                
                ══════════════════════════════════════════════════════════
                TASK:
                ══════════════════════════════════════════════════════════
                
                Respond warmly and briefly in %s (1-2 sentences only).
                
                Guidelines:
                - If greeting: Greet back warmly
                - If thanking: Acknowledge graciously
                - If asking who you are: Briefly explain you help with Georgian statistics
                - Keep it friendly and professional
                - Optionally mention you can help find statistics
                
                DO NOT:
                - Give long explanations
                - Go off-topic
                - Be overly formal
                
                ══════════════════════════════════════════════════════════
                
                Your response in %s (1-2 sentences):
                """,
                userMessage,
                isGeorgian ? "Georgian language" : "English language",
                isGeorgian ? "Georgian language" : "English language"
        );

        try {
            log.info("💬 Generating small talk response in {}", language);
            String response = chatClient.prompt()
                    .user(smallTalkPrompt)
                    .call()
                    .content();

            if (response == null) {
                throw new IllegalStateException("Small talk LLM returned null response");
            }

            return response.trim();
        } catch (Exception e) {
            log.error(" Small talk failed", e);
            return isGeorgian
                    ? "გამარჯობა! როგორ შემიძლია დაგეხმაროთ სტატისტიკის მოძებნაში?"
                    : "Hello! How can I help you find statistics?";
        }
    }
}
