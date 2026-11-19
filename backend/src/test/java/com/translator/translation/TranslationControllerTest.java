package com.translator.translation;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;

import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TranslationControllerTest {
    private static final String VALID_REQUEST = "{\"text\":\"hello\",\"sourceLanguage\":\"en\",\"targetLanguage\":\"nl\"}";
    private ChatModel model;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OllamaChatOptions.builder().model("gemma3:4b").build());
        ChatClient client = new TranslationConfiguration().translationChatClient(model,
                new ClassPathResource("prompts/translation.st"));
        LanguageCatalog catalog = new LanguageCatalog();
        mvc = controller(new TranslationService(client, catalog, 1), catalog);
    }

    @Test
    void returnsCatalogWithEnglishLabelsAndDirection() throws Exception {
        mvc.perform(get("/api/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(44))
                .andExpect(jsonPath("$[1].code").value("ar"))
                .andExpect(jsonPath("$[1].label").value("Arabic"))
                .andExpect(jsonPath("$[1].rtl").value(true));
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void returnsBothTranslationStrings() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                                new Generation(new AssistantMessage("{\"formal\":\"Goedendag\",\"informal\":\"Hoi\"}")))));
        mvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formal").value("Goedendag"))
                .andExpect(jsonPath("$.informal").value("Hoi"));
        verify(model, times(1)).call(any(Prompt.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "[]",
            "{\"text\":\"private source text\",\"sourceLanguage\":\"en\",\"targetLanguage\":\"en\"}",
            "{\"text\":\"private source text\",\"sourceLanguage\":\"xx\",\"targetLanguage\":\"nl\"}",
            "{\"text\":\"private source text\",\"sourceLanguage\":\"auto\",\"targetLanguage\":\"nl\"}",
            "{\"text\":\"private source text\",\"sourceLanguage\":\"\",\"targetLanguage\":\"nl\"}",
            "{\"text\":\"private source text\",\"sourceLanguage\":\"en\",\"targetLanguage\":\"\"}",
            "{\"text\":\"private source text\",\"sourceLanguage\":\"en\",\"targetLanguage\":\"auto\"}",
            "{\"text\":\" \",\"sourceLanguage\":\"en\",\"targetLanguage\":\"nl\"}",
            "{\"text\":null,\"sourceLanguage\":\"en\",\"targetLanguage\":\"nl\"}",
            "{\"text\":\"private source text\",\"sourceLanguage\":null,\"targetLanguage\":\"nl\"}",
            "{\"text\":\"private source text\",\"sourceLanguage\":\"en\",\"targetLanguage\":null}"})
    void rejectsInvalidInputWithoutEchoingText(String body) throws Exception {
        mvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(content().string(not(containsString("private source text"))));
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void rejectsOversizedText() throws Exception {
        String body = JsonMapper.builder().build().writeValueAsString(
                new TranslationRequest("x".repeat(6001), "en", "nl"));
        mvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void mapsMalformedModelOutputToBadGateway() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("private invalid model output")))));
        mvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(content().string(not(containsString("private invalid model output"))));
    }

    @Test
    void mapsUpstreamFailureWithoutExposingDetails() throws Exception {
        when(model.call(any(Prompt.class))).thenThrow(new ResourceAccessException("private upstream details"));
        mvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(not(containsString("private upstream details"))));
    }

    @ParameterizedTest
    @MethodSource("failureStatuses")
    void mapsControlledErrors(TranslationException.Kind kind, int statusCode) throws Exception {
        TranslationService service = mock(TranslationService.class);
        when(service.translate(any())).thenThrow(new TranslationException(kind));
        controller(service, new LanguageCatalog())
                .perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(statusCode));
    }

    @Test
    void busyResponseIncludesRetryAfter() throws Exception {
        TranslationService service = mock(TranslationService.class);
        when(service.translate(any())).thenThrow(new TranslationException(TranslationException.Kind.BUSY));
        controller(service, new LanguageCatalog())
                .perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));
    }

    private static Stream<Arguments> failureStatuses() {
        return Stream.of(Arguments.of(TranslationException.Kind.INVALID_INPUT, 400),
                Arguments.of(TranslationException.Kind.INVALID_OUTPUT, 502),
                Arguments.of(TranslationException.Kind.UNAVAILABLE, 503),
                Arguments.of(TranslationException.Kind.BUSY, 429));
    }

    private static MockMvc controller(TranslationService service, LanguageCatalog catalog) {
        return MockMvcBuilders.standaloneSetup(new TranslationController(service, catalog))
                .setControllerAdvice(new TranslationErrorHandler()).build();
    }
}