package com.example.lettuce.out_of_order;

import org.springframework.boot.SpringApplication;

public class TestOutOfOrderApplication {

	public static void main(String[] args) {
		SpringApplication.from(OutOfOrderApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
