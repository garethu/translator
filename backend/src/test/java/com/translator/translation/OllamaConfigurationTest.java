package com.translator.translation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class OllamaConfigurationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<JsonNode> REQUESTS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<CountDownLatch> DELAY = new AtomicReference<>();
    private static final HttpServer SERVER = startServer();

    @Autowired
    private TranslationService service;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.ollama.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
        registry.add("translator.connect-timeout", () -> "1s");
        registry.add("translator.read-timeout", () -> "1s");
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Test
    @Timeout(15)
    void wiresNativeSchemaAndBoundsReadTimeoutWithoutRetriesOrModelPulls() {
        TranslationRequest request = new TranslationRequest("Hello {name}", "en", "nl");
        assertThat(service.translate(request)).isEqualTo(new TranslationResponse("Goedendag", "Hoi"));
        assertThat(CALLS.get()).isEqualTo(1);
        assertThat(REQUESTS).hasSize(1);
        assertNativePairedSchema(REQUESTS.getFirst());
        assertThat(userText(REQUESTS.getFirst())).isEqualTo(request.text());

        CountDownLatch release = new CountDownLatch(1);
        DELAY.set(release);
        long started = System.nanoTime();
        try {
            assertThatThrownBy(() -> service.translate(request)).isInstanceOf(TranslationException.class)
                    .extracting("kind").isEqualTo(TranslationException.Kind.UNAVAILABLE);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
            assertThat(CALLS.get()).isEqualTo(2);
            assertThat(REQUESTS).hasSize(2);
            assertThat(userText(REQUESTS.getLast())).isEqualTo(request.text());
            assertNativePairedSchema(REQUESTS.getLast());
        }
        finally {
            DELAY.set(null);
            release.countDown();
        }
        assertThat(service.translate(request)).isEqualTo(new TranslationResponse("Goedendag", "Hoi"));
        assertThat(CALLS.get()).isEqualTo(3);
        assertThat(REQUESTS).hasSize(3);
        assertThat(userText(REQUESTS.getLast())).isEqualTo(request.text());
        assertNativePairedSchema(REQUESTS.getLast());
    }

    private static void assertNativePairedSchema(JsonNode payload) {
        assertThat(payload.path("model").asString()).isEqualTo("gemma3:4b");
        assertThat(payload.path("stream").asBoolean()).isFalse();
        assertThat(payload.path("format").path("type").asString()).isEqualTo("object");
        JsonNode properties = payload.path("format").path("properties");
        assertThat(properties.size()).isEqualTo(2);
        assertThat(properties.path("formal").path("type").asString()).isEqualTo("string");
        assertThat(properties.path("informal").path("type").asString()).isEqualTo("string");
        assertThat(properties.has("text")).isFalse();
        assertThat(payload.path("format").path("additionalProperties").asBoolean()).isFalse();
        assertThat(payload.path("format").path("required"))
            .containsExactlyInAnyOrder(JSON.stringNode("formal"), JSON.stringNode("informal"));
    }

    private static String userText(JsonNode payload) {
        for (JsonNode message : payload.path("messages")) {
            if ("user".equals(message.path("role").asString())) {
                return message.path("content").asString();
            }
        }
        throw new IllegalStateException("Missing user message");
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/api/chat", exchange -> {
                CALLS.incrementAndGet();
                JsonNode payload = JSON.readTree(exchange.getRequestBody().readAllBytes());
                REQUESTS.add(payload);
                CountDownLatch delay = DELAY.get();
                try {
                    if (delay != null) {
                        delay.await(5, TimeUnit.SECONDS);
                    }
                    byte[] response = JSON.writeValueAsString(Map.of(
                            "model", "gemma3:4b", "done", true,
                            "message", Map.of("role", "assistant", "content",
                                    JSON.writeValueAsString(Map.of("formal", "Goedendag", "informal", "Hoi")))))
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                catch (IOException exception) {
                    if (delay == null) {
                        throw exception;
                    }
                }
                finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot start test HTTP server", exception);
        }
    }
}