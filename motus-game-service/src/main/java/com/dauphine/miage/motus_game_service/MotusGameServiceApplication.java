package com.dauphine.miage.motus_game_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MotusGameServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MotusGameServiceApplication.class, args);
	}

}
