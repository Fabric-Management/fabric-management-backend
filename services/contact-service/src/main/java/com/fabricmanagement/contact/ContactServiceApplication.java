package com.fabricmanagement.contact;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {
    "com.fabricmanagement.contact",
    "com.fabricmanagement.common.core",
    "com.fabricmanagement.common.security"
})
@EnableFeignClients
@EnableAsync
@EnableTransactionManagement
public class ContactServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContactServiceApplication.class, args);
    }
}
