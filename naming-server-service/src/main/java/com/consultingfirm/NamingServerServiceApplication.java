package com.consultingfirm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class NamingServerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NamingServerServiceApplication.class, args);
	}

}
