package com.music.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MusicRecommendationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicRecommendationApplication.class, args);
    }
}