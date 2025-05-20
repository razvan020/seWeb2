package com.example.bookrecommendation;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BookRecommendationApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        System.setProperty("GOOGLE_AI_API_KEY", dotenv.get("GOOGLE_AI_API_KEY"));
        System.setProperty("GOOGLE_AI_PROJECT_ID", dotenv.get("GOOGLE_AI_PROJECT_ID"));
        SpringApplication.run(BookRecommendationApplication.class, args);
    }
}