package com.khetisetu.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableAsync
@EnableCaching
@RestController
public class NotificationEventServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(NotificationEventServiceApplication.class, args);
	}

	@GetMapping("/health")
	public String health() {
		return "OK - Kafka Consumer Running";
	}
}
