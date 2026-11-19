package com.translator.translation;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleCallTranslationTest {
    @Test
    void returnsBothVersionsFromExactlyOneModelCall() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OllamaChatOptions.builder().model("gemma3:4b").build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("{\"formal\":\"Kunt u het rapport sturen?\",\"informal\":\"Kun je het rapport sturen?\"}")))));
        ChatClient client = new TranslationConfiguration().translationChatClient(model,
                new ClassPathResource("prompts/translation.st"));
        TranslationService service = new TranslationService(client, new LanguageCatalog(), 1);

        assertThat(service.translate(new TranslationRequest("Could you send the report?", "en", "nl")))
                .isEqualTo(new TranslationResponse("Kunt u het rapport sturen?", "Kun je het rapport sturen?"));
        verify(model, times(1)).call(any(Prompt.class));
    }
}