package Chatbot.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final QuestionClassifier questionClassifier;
    private final NavigationService navigationService;
    private final ConversationService conversationService;

    public ChatService(
            @Qualifier("anthropicChatModel") ChatModel chatModel,
            WebClient.Builder webClientBuilder,
            @Value("${geostat.bot.api_key}") String pseApiKey,
            @Value("${geostat.bot.cx_id}") String pseCxId
    ) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        WebClient webClient = webClientBuilder
                .baseUrl("https://www.googleapis.com/customsearch/v1")
                .build();
        ObjectMapper objectMapper = new ObjectMapper();

        this.questionClassifier = new QuestionClassifier(chatClient, objectMapper);
        this.navigationService = new NavigationService(chatClient, webClient, objectMapper, pseApiKey, pseCxId);
        this.conversationService = new ConversationService(chatClient);
    }

    public String getChatResponse(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "გთხოვთ, შეიყვანოთ შეკითხვა.";
        }

        userMessage = userMessage.trim();
        log.info(" User message length: {} chars", userMessage.length());

        if (LanguageUtils.isSimpleGreeting(userMessage)) {
            return LanguageUtils.getGreetingResponse(userMessage);
        }

        QueryPlan plan = questionClassifier.classify(userMessage);
        log.info(" Classification: intent={}, topic={}, queries={}",
                plan.intent, plan.topic, plan.searchQueries);

        switch (plan.intent) {
            case "small_talk":
                return conversationService.handleSmallTalk(userMessage, plan.language);
            case "general_knowledge":
                return conversationService.handleGeneralKnowledge(userMessage, plan.language);
            case "navigation":
            default:
                return navigationService.handleNavigation(userMessage, plan);
        }
    }
}
