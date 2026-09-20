package com.globo.fintech_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FintechBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FintechBackendApplication.class, args);
	}

}
