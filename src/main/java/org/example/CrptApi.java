package org.example;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {
    private final HttpClient httpClient;
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final int requestLimit;
    private final AtomicInteger requestCount;
    private final Semaphore semaphore;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.requestLimit = requestLimit;
        this.requestCount = new AtomicInteger(0);
        this.semaphore = new Semaphore(requestLimit);

        long delay = timeUnit.toMillis(1);
        System.out.println(delay);
        scheduler.scheduleAtFixedRate(() -> {
            requestCount.set(0);
            semaphore.release(requestLimit - semaphore.availablePermits());
        }, delay, delay, TimeUnit.MILLISECONDS);
    }

    public void createDocumentFromFile(String filePath, String signature) throws IOException, InterruptedException {
        semaphore.acquire();

        String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));
        Document document = objectMapper.readValue(jsonContent, Document.class);
        String requestBody = createRequestBody(document, signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to create document: " + response.body());
        }

        requestCount.incrementAndGet();
    }

    private String createRequestBody(Document document, String signature) throws JsonProcessingException {
        // Here you can add the signature to the request body if needed
        // For example, you can include it as a field in the Document class
        return objectMapper.writeValueAsString(document);
    }

    public static class Document {
        // Fields for the document
        public String participantInn;
        public String docId;
        public String docStatus;
        public String docType ;
        public boolean importRequest;
        public String ownerInn;
        public String producerInn;
        public String productionDate;
        public String productionType;
        public Product[] products;
        public String regDate;
        public String regNumber;

        public static class Product {
            public String certificateDocument;
            public String certificateDocumentDate;
            public String certificateDocumentNumber;
            public String ownerInn;
            public String producerInn;
            public String productionDate;
            public String tnvedCode;
            public String uitCode;
            public String uituCode;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5);

        String filePath = "src/main/resources/document.json";  // json file path
        String signature = "signature";

        crptApi.createDocumentFromFile(filePath, signature);
    }
}