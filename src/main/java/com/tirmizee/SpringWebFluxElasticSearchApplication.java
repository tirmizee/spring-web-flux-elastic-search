package com.tirmizee;

import com.tirmizee.service.PromotionMockService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringWebFluxElasticSearchApplication {

	public static void main(String[] args) {
		var context = SpringApplication.run(SpringWebFluxElasticSearchApplication.class, args);
		var service = context.getBean(PromotionMockService.class);

		service.generateAndInsertMockData()
				.subscribe();

	}

}
