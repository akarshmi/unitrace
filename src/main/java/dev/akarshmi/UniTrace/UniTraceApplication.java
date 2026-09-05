package dev.akarshmi.UniTrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@SpringBootApplication
@EnableRedisRepositories
public class UniTraceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UniTraceApplication.class, args);
	}

}
