package com.tirmizee.service;

import com.tirmizee.document.PaymentLogDocument;
import com.tirmizee.repository.PaymentLogSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentLogSearchService {

    private final PaymentLogSearchRepository repository;

    public Mono<PaymentLogDocument> saveLog(
            String orderId,
            String status,
            String message
    ) {
        PaymentLogDocument document = new PaymentLogDocument(
                UUID.randomUUID().toString(),
                orderId,
                status,
                message,
                Instant.now()
        );

        return repository.save(document);
    }

    public Mono<Long> deleteByOrderId(String orderId) {
        return repository.findByOrderId(orderId)
                .flatMap(document ->
                        repository.delete(document).thenReturn(1L)
                )
                .reduce(0L, Long::sum);
    }

    public Flux<PaymentLogDocument> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId);
    }

    public Flux<PaymentLogDocument> findByStatus(String status) {
        return repository.findByStatus(status);
    }


}
