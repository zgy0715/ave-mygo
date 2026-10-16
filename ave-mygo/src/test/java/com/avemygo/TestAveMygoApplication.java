package com.avemygo;

import org.springframework.boot.SpringApplication;

public class TestAveMygoApplication {

    public static void main(String[] args) {
        SpringApplication.from(AveMygoApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
