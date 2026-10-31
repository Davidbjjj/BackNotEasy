package com.example.BancoDeDados;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@EnableScheduling
@EnableCaching
public class BancoDeDadosApplication {

	public static void main(String[] args) {
		SpringApplication.run(BancoDeDadosApplication.class, args);

	}

}
