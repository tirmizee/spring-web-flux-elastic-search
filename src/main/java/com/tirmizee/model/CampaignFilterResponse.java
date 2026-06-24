package com.tirmizee.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
@Builder
@Jacksonized
@Value
public class CampaignFilterResponse {
    String partialSearch;
    String sortId;
    int page;
    int pageSize;
    List<Campaign> campaign;
}
