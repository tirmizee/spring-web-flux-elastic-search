package com.tirmizee.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@Data
public class CampaignFilterRequest {
    Double userLat;
    Double userLon;
    Integer radiusKm;

    String language;
    String partialSearch;
    String sortId;
    int page;
    int pageSize;
}
