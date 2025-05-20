package com.example.cacheservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CacheserviceApplication {

	@Bean
	public Logger logger(){
		return LoggerFactory.getLogger("ApplicationLogger");
	}

	public static void main(String[] args) {
		SpringApplication.run(CacheserviceApplication.class, args);
	}

}
