package com.tirmizee.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.tirmizee.document.PromotionDocument;
import com.tirmizee.model.CampaignFilterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionSearchV1Service {

    private final ReactiveElasticsearchOperations elasticsearchOperations;

    public Mono<List<PromotionDocument>> searchCampaigns(CampaignFilterRequest request) {

        // 1. กำหนด Pagination และ Sorting
        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getPageSize(),
                getSortLogic(request.getSortId())
        );

        // 2. ประกอบ Query ด้วย Elasticsearch Java API Client
        Query esQuery = Query.of(q -> q.bool(b -> {

            // 2.1 บังคับว่าต้องตรงกับ Language ที่ส่งมา (Filter)
            b.filter(f -> f.term(t -> t.field("language").value(request.getLanguage())));

            // 2.2 ถ้ามีการส่งคำค้นหา (Partial Search) มาด้วย
            if (request.getPartialSearch() != null && !request.getPartialSearch().trim().isEmpty()) {

                // สลับไปค้นหาในฟิลด์เงาที่ถูกต้องตามภาษาของ User
                String targetNameField = "th".equalsIgnoreCase(request.getLanguage()) ? "name.th" : "name.en";
                String targetDetailField = "th".equalsIgnoreCase(request.getLanguage()) ? "detail.th" : "detail.en";

                b.must(must -> must.multiMatch(mm -> mm
                        .query(request.getPartialSearch())
                        // เติม ^3 เพื่อให้น้ำหนักคะแนนว่า ถ้าเจอคำนี้ใน "ชื่อ" ให้คะแนนความแม่นยำสูงกว่าเจอใน "รายละเอียด" 3 เท่า
                        .fields(List.of(targetNameField + "^3", targetDetailField))
                        .fuzziness("AUTO") // เปิดฟีเจอร์ Typo-tolerant (พิมพ์ผิดนิดหน่อยก็หาเจอ)
                ));
            }
            return b;
        }));

        // 3. ห่อ Query เข้ากับ NativeQuery ของ Spring Data
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(esQuery)
                .withPageable(pageable)
                .build();

        // 4. สั่งค้นหาและ Transform ผลลัพธ์กลับเป็น List ปกติ
        return elasticsearchOperations.search(nativeQuery, PromotionDocument.class)
                .map(SearchHit::getContent) // ดึงเฉพาะเนื้อหา Document ออกมาจาก SearchHit
                .collectList();
    }

    private Sort getSortLogic(String sortId) {
        if (sortId == null) return Sort.unsorted();

        return switch (sortId) {
            case "1" -> Sort.by(Sort.Direction.DESC, "effectiveDate"); // ใหม่สุดขึ้นก่อน
            case "2" -> Sort.by(Sort.Direction.ASC, "expireDate");   // กำลังจะหมดอายุขึ้นก่อน
            default -> Sort.unsorted(); // เรียงตามความแม่นยำ (Score) ที่ ES คำนวณให้
        };
    }

}
