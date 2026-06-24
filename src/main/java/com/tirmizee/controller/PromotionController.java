package com.tirmizee.controller;

import com.tirmizee.document.PromotionDocument;
import com.tirmizee.model.CampaignFilterRequest;
import com.tirmizee.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class PromotionController {

    private final PromotionSearchV1Service searchService;
    private final PromotionUpsertService upsertService;
    private final PromotionSearchV2Service promotionSearchV2Service;
    private final PromotionSearchV3Service promotionSearchV3Service;
    private final PromotionIndexResetService promotionIndexResetService;

    @PostMapping("/v1/filter")
    public Mono<List<PromotionDocument>> filterV1(@RequestBody final CampaignFilterRequest request) {
        return searchService.searchCampaigns(request);
    }

    @PostMapping("/v2/filter")
    public Mono<List<PromotionDocument>> filterV2(@RequestBody final CampaignFilterRequest request) {
        return promotionSearchV2Service.searchCampaigns(request);
    }

    @PostMapping("/v3/filter")
    public Mono<List<PromotionDocument>> filterV3(@RequestBody final CampaignFilterRequest request) {
        return promotionSearchV3Service.searchCampaigns(request);
    }

    @PostMapping("/refresh")
    public Mono<String> refresh() {
        return upsertService.refresh();
    }

    @PostMapping("/reset")
    public Mono<String> reset() {
        return promotionIndexResetService.resetIndexAndRecreateData();
    }

}
