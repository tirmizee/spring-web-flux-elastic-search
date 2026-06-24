package com.tirmizee.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.tirmizee.document.PromotionDocument;
import com.tirmizee.model.CampaignFilterRequest;
import com.tirmizee.utility.CampaignSearchHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionSearchV3Service {

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

        // กรณีที่ 2: ถ้าไม่มี Keyword (เปิดหน้าแรกมาเฉยๆ) ให้ดึงข้อมูลปกติตามภาษาและพิกัดแผนที่
        return fetchNormal(request);
    }

    // ==========================================
    // Phase 1: ค้นหาข้ามภาษา เพื่อดึงแค่ promotionCode
    // ==========================================
    private Mono<List<String>> phaseOneSearchCodes(CampaignFilterRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), CampaignSearchHelper.getSortLogic(request));

        String keyword = request.getPartialSearch().trim();
        String wildcardKeyword = "*" + keyword + "*";

        Query esQuery = Query.of(q -> q.bool(b -> {
            b.must(m -> m.bool(innerBool -> innerBool
                    // 💡 อัปเดต 2: ค้นหาแบบก้อนวลี (Match Phrase)
                    .should(s -> s.multiMatch(mm -> mm
                            .query(keyword)
                            .fields(List.of("name.th^3", "name.en^3", "detail.th", "detail.en"))
                            .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.Phrase)
                    ))
                    // 💡 อัปเดต 3: ค้นหาแบบตรงเป๊ะ (บังคับ Operator.And) หรือใช้ Minimum Should Match
                    .should(s -> s.multiMatch(mm -> mm
                            .query(keyword)
                            .fields(List.of("name.th^3", "name.en^3", "detail.th", "detail.en"))
//                            .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                            .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.Or) //  OR เป็น Default ถ้าไม่ใส่
                            .minimumShouldMatch("70%")
                    ))
                    // 💡 อัปเดต 4: ค้นหาแบบ Typo-Tolerant (ลด Fuzziness และบังคับ Operator.And)
                    .should(s -> s.multiMatch(mm -> mm
                            .query(keyword)
                            .fields(List.of("name.th^3", "name.en^3", "detail.th", "detail.en"))
                            .fuzziness("1")
                            .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                    ))
                    // 💡 อัปเดต 5: ค้นหาแบบ Substring/Wildcard (บังคับ Operator.And)
                    .should(s -> s.queryString(qs -> qs
                            .query(wildcardKeyword)
                            .fields(List.of("name.th^3", "name.en^3", "detail.th", "detail.en"))
                            .defaultOperator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                    ))
            ));

            // 2. แปะเงื่อนไขพิกัดแผนที่ ถ้าผู้ใช้ส่งมา (FILTER)
            CampaignSearchHelper.applyGeoFilterIfPresent(b, request);

            return b;
        }));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(esQuery)
                .withPageable(pageable)
                .withFields("promotionCode") // ทริคประหยัดแรมดึงเฉพาะรหัส
                .build();

        return elasticsearchOperations.search(nativeQuery, PromotionDocument.class)
                .doOnNext(hit -> System.out.println("keyword: " + keyword + " | รหัส: " + hit.getContent().getPromotionCode() + " | คะแนน: " + hit.getScore()))
                .map(hit -> hit.getContent().getPromotionCode())
                .distinct()
                .collectList();
    }

    // ==========================================
    // Phase 2: เอา promotionCode มาดึง Document ตามภาษา
    // ==========================================
    private Mono<List<PromotionDocument>> phaseTwoFetchDocuments(List<String> codes, CampaignFilterRequest request) {
        Pageable pageable = PageRequest.of(0, codes.size(), CampaignSearchHelper.getSortLogic(request));

        Query esQuery = Query.of(q -> q.bool(b -> b
                .must(m -> m.term(t -> t.field("language").value(request.getLanguage())))
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
                .collectList()
                .map(docs -> {
                    // 💡 อัปเดต 6: นำผลลัพธ์ที่ได้มาเข้าแถวเรียงลำดับใหม่ให้ตรงกับ Array 'codes' จาก Phase 1
                    // (ทำงานเฉพาะกรณีที่ไม่ได้ค้นหาด้วยแผนที่ เพราะถ้าหาแผนที่มันจะถูกจัดเรียงมาถูกต้องแล้ว)
                    if (request.getUserLat() == null && request.getSortId() == null) {
                        docs.sort(Comparator.comparingInt(doc -> codes.indexOf(doc.getPromotionCode())));
                    }
                    return docs;
                });
    }

    // ==========================================
// ดึงข้อมูลปกติ (ไม่ค้นหา Keyword)
// ==========================================
    private Mono<List<PromotionDocument>> fetchNormal(CampaignFilterRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), CampaignSearchHelper.getSortLogic(request));

        Query esQuery = Query.of(q -> q.bool(b -> {
            // 1. ต้องตรงกับภาษาที่ร้องขอ
            b.must(m -> m.term(t -> t.field("language").value(request.getLanguage())));

            // 2. แปะเงื่อนไขพิกัดแผนที่ ถ้าผู้ใช้ส่งมา
            CampaignSearchHelper.applyGeoFilterIfPresent(b, request);

            return b;
        }));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(esQuery)
                .withPageable(pageable)
                .build();

        return elasticsearchOperations.search(nativeQuery, PromotionDocument.class)
                .map(SearchHit::getContent)
                .collectList();
    }

}
