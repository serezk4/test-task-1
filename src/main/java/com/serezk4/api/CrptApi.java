package com.serezk4.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * libs: jackson & lombok
 *
 * @author serezk4
 * @version 1.0
 */

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrptApi {
    public static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    HttpClient httpClient;
    ObjectMapper objectMapper;

    ScheduledExecutorService scheduler;
    AtomicInteger requestCount;
    int requestLimit;
    long timeUnitMillis;

    Lock lock = new ReentrantLock();

    /**
     * Base constructor
     *
     * @param timeUnit     - time unit for request limit
     * @param requestLimit - request limit
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        this.requestCount = new AtomicInteger();
        this.requestLimit = requestLimit;
        this.timeUnitMillis = timeUnit.toMillis(1);
        this.scheduler = Executors.newScheduledThreadPool(1);

        startScheduler();
    }

    /**
     * Method for starting {@link #scheduler}
     */
    private void startScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                requestCount.set(0);
            } finally {
                lock.unlock();
            }
        }, timeUnitMillis, timeUnitMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Method for waiting for a free slot to send a new request
     */
    private void waitForSlot() {
        lock.lock();
        try {
            while (requestCount.get() >= requestLimit) TimeUnit.MILLISECONDS.sleep(100);
            requestCount.incrementAndGet();
        } catch (InterruptedException e) {
            shutdown();
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Method for shutting down {@link #scheduler}
     */
    public void shutdown() {
        scheduler.shutdown();
    }


    /**
     * Method for creating a document in CRPT
     *
     * @param document  - Document record
     * @param signature - Signature
     * @return - Response body as a string
     * @throws IOException          - If an I/O error occurs
     * @throws InterruptedException - If the current thread is interrupted
     */
    public String createDocument(Document document, String signature) throws IOException, InterruptedException {
        waitForSlot();

        String requestBody = objectMapper.writeValueAsString(document);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }


    /**
     * Document record
     *
     * @param description    - Description
     * @param docId          - Document ID
     * @param docStatus      - Document status
     * @param docType        - Document type
     * @param importRequest  - Import request
     * @param ownerInn       - Owner INN
     * @param participantInn - Participant INN
     * @param producerInn    - Producer INN
     * @param productionDate - Production date
     * @param productionType - Production type
     * @param products       - Array of products {@link Product}
     * @param regDate        - Registration date
     * @param regNumber      - Registration number
     */
    public record Document(
            String description,
            String docId,
            String docStatus,
            String docType,
            boolean importRequest,
            String ownerInn,
            String participantInn,
            String producerInn,
            String productionDate,
            String productionType,
            Product[] products,
            String regDate,
            String regNumber
    ) {

        /**
         * Product record
         *
         * @param certificateDocument       - Certificate document
         * @param certificateDocumentDate   - Certificate document date
         * @param certificateDocumentNumber - Certificate document number
         * @param ownerInn                  - Owner INN
         * @param producerInn               - Producer INN
         * @param productionDate            - Production date
         * @param tnvedCode                 - TNVED code
         * @param uitCode                   - UIT code
         * @param uituCode                  - UITU code
         */
        public record Product(
                String certificateDocument,
                String certificateDocumentDate,
                String certificateDocumentNumber,
                String ownerInn,
                String producerInn,
                String productionDate,
                String tnvedCode,
                String uitCode,
                String uituCode
        ) {

        }
    }
}
