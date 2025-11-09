package com.khetisetu.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableAsync
@EnableCaching
public class NotificationEventServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(NotificationEventServiceApplication.class, args);
	}
}
