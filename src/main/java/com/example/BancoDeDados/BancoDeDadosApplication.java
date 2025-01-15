package com.example.BancoDeDados;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
class  BancoDeDadosApplication {

	public static void main(String[] args) {
		SpringApplication.run(BancoDeDadosApplication.class, args);

	}

}
