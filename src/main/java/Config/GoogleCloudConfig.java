package Config;

import com.google.cloud.speech.v1.SpeechClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GoogleCloudConfig {

    @Bean
    public SpeechClient speechClient() throws IOException {
        return SpeechClient.create();
    }
}