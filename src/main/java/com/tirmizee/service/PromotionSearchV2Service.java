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
public class PromotionSearchV2Service {

    private final ReactiveElasticsearchOperations elasticsearchOperations;

    public Mono<List<PromotionDocument>> searchCampaigns(CampaignFilterRequest request) {

        // กรณีที่ 1: ถ้ามีการส่ง Keyword มาค้นหา (ต้องทำ Two-Phase Query)
        if (request.getPartialSearch() != null && !request.getPartialSearch().trim().isEmpty()) {
            return phaseOneSearchCodes(request)
                    .flatMap(codes -> {
                        // ถ้าไม่เจอโปรโมชันเลย ให้คืนค่า List ว่างกลับไปทันที
                        if (codes.isEmpty()) {
                            return Mono.just(List.of());
                        }
                        // เจอ Code แล้ว ให้ไปดึงข้อมูลตามภาษาที่ต้องการ
                        return phaseTwoFetchDocuments(codes, request);
                    });
        }

        // กรณีที่ 2: ถ้าไม่มี Keyword (เปิดหน้าแรกมาเฉยๆ) ให้ดึงข้อมูลปกติตามภาษาได้เลย
        return fetchNormal(request);
    }

    // ==========================================
    // Phase 1: ค้นหาข้ามภาษา เพื่อดึงแค่ promotionCode
    // ==========================================
    private Mono<List<String>> phaseOneSearchCodes(CampaignFilterRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), getSortLogic(request.getSortId()));

        String keyword = request.getPartialSearch().trim();
        String wildcardKeyword = "*" + keyword + "*";

        Query esQuery = Query.of(q -> q.bool(b -> b
                .must(m -> m.bool(innerBool -> innerBool
                        // 1. ค้นหาแบบตรงเป๊ะ + ดึง Synonym มาใช้ (ห้ามใส่ Fuzziness เด็ดขาด!)
                        .should(s -> s.multiMatch(mm -> mm
                                .query(keyword)
                                .fields(List.of("name.th^3", "name.en^3", "detail.th", "detail.en"))
                        ))
                        // 2. ค้นหาแบบ Typo-Tolerant (เผื่อพิมพ์ผิดเล็กน้อย)
                        .should(s -> s.multiMatch(mm -> mm
                                .query(keyword)
                                .fields(List.of("name.th^3", "name.en^3", "detail.th", "detail.en"))
                                .fuzziness("AUTO")
                        ))
                        // 3. ค้นหาแบบ Substring/Wildcard (เผื่อพิมพ์มาแค่กลางคำ)
                        .should(s -> s.queryString(qs -> qs
                                .query(wildcardKeyword)
                                .fields(List.of("name.th^3", "name.en^3", "detail.th", "detail.en"))
                        ))
                ))
        ));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(esQuery)
                .withPageable(pageable)
                // ทริคประหยัดแรม: สั่งให้ ES ดึงกลับมาแค่ฟิลด์ promotionCode (ไม่ดึง Data ก้อนใหญ่)
                .withFields("promotionCode")
                .build();

        return elasticsearchOperations.search(nativeQuery, PromotionDocument.class)
                .map(hit -> hit.getContent().getPromotionCode())
                .distinct() // เผื่อกรณีซ้ำ
                .collectList();
    }

    // ==========================================
    // Phase 2: เอา promotionCode มาดึง Document ตามภาษา
    // ==========================================
    private Mono<List<PromotionDocument>> phaseTwoFetchDocuments(List<String> codes, CampaignFilterRequest request) {
        // ไม่ต้องทำ Pagination แล้ว เพราะเรากรองจำนวนมาจาก Phase 1 แล้ว
        Pageable pageable = PageRequest.of(0, codes.size(), getSortLogic(request.getSortId()));

        Query esQuery = Query.of(q -> q.bool(b -> b
                // 1. กรองเอาเฉพาะภาษาที่ Request ขอมา (เช่น "en")
                .must(m -> m.term(t -> t.field("language").value(request.getLanguage())))
                // 2. กรองเอาเฉพาะกลุ่ม promotionCode ที่หาเจอจาก Phase 1
                .filter(f -> f.terms(t -> t.field("promotionCode").terms(terms ->
                        terms.value(codes.stream().map(co.elastic.clients.elasticsearch._types.FieldValue::of).toList())
                )))
        ));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(esQuery)
                .withPageable(pageable)
                .build();

        return elasticsearchOperations.search(nativeQuery, PromotionDocument.class)
                .map(SearchHit::getContent)
                .collectList();
    }

    // ==========================================
    // ดึงข้อมูลปกติ (ไม่ค้นหา Keyword)
    // ==========================================
    private Mono<List<PromotionDocument>> fetchNormal(CampaignFilterRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), getSortLogic(request.getSortId()));

        Query esQuery = Query.of(q -> q.bool(b -> b
                .must(m -> m.term(t -> t.field("language").value(request.getLanguage())))
        ));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(esQuery)
                .withPageable(pageable)
                .build();

        return elasticsearchOperations.search(nativeQuery, PromotionDocument.class)
                .map(SearchHit::getContent)
                .collectList();
    }

    // Helper: สร้างเงื่อนไขการเรียงลำดับ
    private Sort getSortLogic(String sortId) {
        if (sortId == null) return Sort.unsorted();
        return switch (sortId) {
            case "1" -> Sort.by(Sort.Direction.DESC, "effectiveDate");
            case "2" -> Sort.by(Sort.Direction.ASC, "expireDate");
            default -> Sort.unsorted();
        };
    }

}
