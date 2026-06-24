package com.tirmizee.service;

import com.tirmizee.document.PromotionDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ReactiveIndexOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionIndexResetService {

    private final ReactiveElasticsearchOperations operations;
    private final PromotionUpsertService promotionService;

    public Mono<String> resetIndexAndRecreateData() {

        ReactiveIndexOperations indexOps = operations.indexOps(PromotionDocument.class);

        log.info("=== Starting Hard Reset Process ===");

        return indexOps.exists()
                .flatMap(exists -> {
                    if (exists) {
                        log.info("Step 1: Found existing index, deleting...");
                        return indexOps.delete();
                    } else {
                        log.info("Step 1: Index does not exist, skipping delete.");
                        return Mono.just(true); // ข้ามไปทำสเต็ปถัดไป
                    }
                })
                .then(indexOps.create())
                .doOnSuccess(created -> log.info("Step 2: New index created."))
                .then(indexOps.putMapping(PromotionDocument.class))
                .doOnSuccess(mapped -> log.info("Step 3: Index mapping applied successfully."))
                .then(promotionService.refresh())
                .map(insertResult -> {
                    log.info("Step 4: Data insertion completed.");
                    return "Hard Reset Completed Successfully! \nStatus: " + insertResult;
                })
                .onErrorResume(error -> {
                    log.error("Hard Reset Failed!", error);
                    return Mono.just("Failed to reset index: " + error.getMessage());
                });
    }

}
