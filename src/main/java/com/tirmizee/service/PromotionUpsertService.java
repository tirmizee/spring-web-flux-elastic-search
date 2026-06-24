package com.tirmizee.service;

import com.tirmizee.document.PromotionDocument;
import com.tirmizee.repository.PromotionDocumentSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionUpsertService {

    private final PromotionDocumentSearchRepository promotionRepository;
    private final PromotionMockService promotionMockService;

    public Mono<String> refresh() {
        log.info("Starting Bulk Upsert Process...");

        // promotionService.generateAndInsertMockData() ข้างในใช้ .saveAll() อยู่แล้ว
        return promotionMockService.generateAndInsertMockData()
                .map(result -> "Upsert Process Completed: All documents created or updated successfully!");
    }

    // 2. แบบ Single Upsert: อัปเดตหรือสร้างใหม่แค่ 1 แคมเปญ (รับมาจาก Request)
    public Mono<PromotionDocument> upsertSingleCampaign(PromotionDocument document) {
        // ถ้า document มี ID ตรงกับในระบบ มันจะ Update
        // ถ้าไม่มี ID นี้ในระบบ มันจะ Insert ใหม่
        return promotionRepository.save(document);
    }

}
