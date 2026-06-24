package com.tirmizee.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "payment-logs")
public class PaymentLogDocument {

    @Id
    private String id;

    private String orderId;
    private String status;
    private String message;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;
}