package com.tirmizee.repository;

import com.tirmizee.document.PromotionDocument;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;

public interface PromotionDocumentSearchRepository extends ReactiveElasticsearchRepository<PromotionDocument, String> {

}
