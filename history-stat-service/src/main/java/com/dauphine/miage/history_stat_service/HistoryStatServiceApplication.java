package com.dauphine.miage.history_stat_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HistoryStatServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(HistoryStatServiceApplication.class, args);
	}

}
