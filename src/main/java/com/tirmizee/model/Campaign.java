package com.tirmizee.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@Value
public class Campaign {
    String id;
    String name;
    String detail;
    String link;
    String effectiveDate;
    String expireDate;
    String promotionCode;
    String imageUrl;
    String type;
    String registrationStatus;
    Boolean combinedFlag;
    Integer mechanic;
}