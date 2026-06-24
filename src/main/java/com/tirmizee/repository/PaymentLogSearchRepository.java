package com.tirmizee.repository;

import com.tirmizee.document.PaymentLogDocument;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import reactor.core.publisher.Flux;

public interface PaymentLogSearchRepository extends ReactiveElasticsearchRepository<PaymentLogDocument, String> {

    Flux<PaymentLogDocument> findByOrderId(String orderId);

    Flux<PaymentLogDocument> findByStatus(String status);
}
