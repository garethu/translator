package com.translator.translation;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class TranslationConfiguration {
    @Bean
    RestClient ollamaStatusClient(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${translator.connect-timeout}") Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    RetryTemplate translationRetryTemplate() {
        return new RetryTemplate(RetryPolicy.withMaxRetries(0));
    }

    @Bean
    OllamaApi ollamaApi(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${translator.connect-timeout}") Duration connectTimeout,
            @Value("${translator.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return OllamaApi.builder().baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(factory)).build();
    }

    @Bean
    ChatClient translationChatClient(ChatModel chatModel,
            @Value("classpath:prompts/translation.st") Resource systemPrompt) {
        return ChatClient.builder(chatModel).defaultSystem(systemPrompt).build();
    }
}