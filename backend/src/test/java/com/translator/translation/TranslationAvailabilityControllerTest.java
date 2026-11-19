package com.translator.translation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Timeout(10)
class TranslationAvailabilityControllerTest {
    private HttpServer server;
    private MockMvc mvc;
    private final AtomicInteger upstreamStatus = new AtomicInteger(200);
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/version", exchange -> {
            try (exchange) {
                if (upstreamStatus.get() == 0) {
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                else {
                    exchange.sendResponseHeaders(upstreamStatus.get(), -1);
                }
            }
        });
        server.start();
        var client = new TranslationConfiguration().ollamaStatusClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofMillis(200));
        mvc = MockMvcBuilders.standaloneSetup(new TranslationAvailabilityController(client)).build();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        server.stop(0);
    }

    @Test
    void checksOllamaAndRecoversWithoutCaching() throws Exception {
        mvc.perform(get("/api/availability"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        upstreamStatus.set(503);
        mvc.perform(get("/api/availability"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"));
        upstreamStatus.set(200);
        mvc.perform(get("/api/availability")).andExpect(status().isOk());
    }

    @Test
    void reportsStoppedOllamaAsUnavailable() throws Exception {
        server.stop(0);
        mvc.perform(get("/api/availability")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void reportsUnresponsiveOllamaAsUnavailable() throws Exception {
        upstreamStatus.set(0);
        mvc.perform(get("/api/availability")).andExpect(status().isServiceUnavailable());
    }
}
