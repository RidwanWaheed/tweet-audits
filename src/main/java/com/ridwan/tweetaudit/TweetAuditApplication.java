package com.ridwan.tweetaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class TweetAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(TweetAuditApplication.class, args);
    }

}
