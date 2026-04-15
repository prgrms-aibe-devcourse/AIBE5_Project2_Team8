package com.knoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
@EnableJpaAuditing
public class KnocApplication {

	public static void main(String[] args) {
		SpringApplication.run(KnocApplication.class, args);
	}

}
