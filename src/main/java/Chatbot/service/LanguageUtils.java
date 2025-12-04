package Chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public final class LanguageUtils {

    private static final Logger log = LoggerFactory.getLogger(LanguageUtils.class);

    private static final Set<String> GREETINGS = Set.of(
            "hi", "hello", "hey",
            "gamarjoba",
            "გამარჯობა", "მოგესალმები"
    );

    private LanguageUtils() {
    }

    public static String detectLanguage(String text) {
        if (text == null || text.isEmpty()) {
            return "ka";
        }

        boolean hasGeorgian = text.matches(".*[ა-ჰ].*");
        return hasGeorgian ? "ka" : "en";
    }

    public static boolean isSimpleGreeting(String msg) {
        if (msg == null) return false;
        String normalized = msg.toLowerCase().replaceAll("[^a-zა-ჰ]", "");
        boolean isGreeting = GREETINGS.contains(normalized);
        if (isGreeting) {
            log.info("👋 Detected simple greeting: {}", normalized);
        }
        return isGreeting;
    }

    public static String getGreetingResponse(String msg) {
        String normalized = msg.toLowerCase().replaceAll("[^a-zა-ჰ]", "");

        if ("gamarjoba".equals(normalized)) {
            log.info("👋 Sending greeting response in ka (romanized)");
            return "გამარჯობა! რა სტატისტიკური მონაცემები გაინტერესებთ?";
        }

        String language = detectLanguage(msg);
        log.info("👋 Sending greeting response in {}", language);
        return language.equals("ka")
                ? "გამარჯობა! რა სტატისტიკური მონაცემები გაინტერესებთ?"
                : "Hello! What statistics are you looking for today?";
    }
}
