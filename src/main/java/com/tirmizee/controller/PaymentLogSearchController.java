package com.tirmizee.controller;

import com.tirmizee.document.PaymentLogDocument;
import com.tirmizee.service.PaymentLogSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/payment-logs")
@RequiredArgsConstructor
public class PaymentLogSearchController {

    private final PaymentLogSearchService service;

    @PostMapping("/{orderId}")
    public Mono<PaymentLogDocument> save(@PathVariable String orderId, @RequestBody CreatePaymentLogRequest request) {
        return service.saveLog(
                orderId,
                request.status(),
                request.message()
        );
    }

    @DeleteMapping("/order/{orderId}")
    public Mono<DeletePaymentLogResponse> deleteByOrderId(
            @PathVariable String orderId
    ) {
        return service.deleteByOrderId(orderId)
                .map(deletedCount -> new DeletePaymentLogResponse(
                        orderId,
                        deletedCount,
                        "Deleted payment logs successfully"
                ));
    }

    @GetMapping("/order/{orderId}")
    public Flux<PaymentLogDocument> findByOrderId(@PathVariable String orderId) {
        return service.findByOrderId(orderId);
    }

    @GetMapping("/status/{status}")
    public Flux<PaymentLogDocument> findByStatus(@PathVariable String status) {
        return service.findByStatus(status);
    }

    public record CreatePaymentLogRequest(String status, String message) { }

    public record DeletePaymentLogResponse(String orderId, Long deletedCount, String message) { }

}
