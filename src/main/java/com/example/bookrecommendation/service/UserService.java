package com.example.bookrecommendation.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@Service
public class UserService {
    private final Map<String, Map<String, Object>> userPreferences;

    public UserService() {
        userPreferences = new HashMap<>();

        // Initialize with some predefined users and their preferences
        Map<String, Object> alicePrefs = new HashMap<>();
        alicePrefs.put("readingLevel", "http://example.org/book/Intermediate");
        alicePrefs.put("preferredThemes", Arrays.asList("http://example.org/book/ScienceFiction"));
        userPreferences.put("Alice", alicePrefs);

        Map<String, Object> bobPrefs = new HashMap<>();
        bobPrefs.put("readingLevel", "http://example.org/book/Beginner");
        bobPrefs.put("preferredThemes", Arrays.asList("http://example.org/book/Mystery"));
        userPreferences.put("Bob", bobPrefs);
    }

    public Map<String, Object> getUserPreferences(String username) {
        return userPreferences.getOrDefault(username, new HashMap<>());
    }

    public List<String> getAvailableUsers() {
        return Arrays.asList("Alice", "Bob");
    }
}