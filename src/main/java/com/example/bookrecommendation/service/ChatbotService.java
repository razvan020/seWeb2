package com.example.bookrecommendation.service;

import com.example.bookrecommendation.model.ChatMessage;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatbotService {

    private final RdfService rdfService;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Map<String, ChatMemory> chatMemories = new ConcurrentHashMap<>();
    private final Map<String, String> currentContext = new ConcurrentHashMap<>();

    private interface BookAssistant {
        String chat(@MemoryId String sessionId, @UserMessage String message);
    }

    @Autowired
    public ChatbotService(RdfService rdfService) {
        this.rdfService = rdfService;
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        this.embeddingStore = new InMemoryEmbeddingStore<>();

        // Initialize the vector database with book data
        initializeVectorDatabase();
    }

    private void initializeVectorDatabase() {
        // Get all books from the RDF model
        List<Map<String, Object>> books = rdfService.getAllBooks();

        // Create documents for each book
        for (Map<String, Object> book : books) {
            StringBuilder documentText = new StringBuilder();
            documentText.append("Title: ").append(book.get("title")).append("\n");

            // Add themes
            documentText.append("Themes: ");
            List<Map<String, String>> themes = (List<Map<String, String>>) book.get("themes");
            for (int i = 0; i < themes.size(); i++) {
                documentText.append(themes.get(i).get("name"));
                if (i < themes.size() - 1) {
                    documentText.append(", ");
                }
            }
            documentText.append("\n");

            // Add reading level
            Map<String, String> readingLevel = (Map<String, String>) book.get("readingLevel");
            if (readingLevel != null) {
                documentText.append("Reading Level: ").append(readingLevel.get("name")).append("\n");
            }

            // Add recommended for users if available
            List<Map<String, String>> recommendedFor = (List<Map<String, String>>) book.get("recommendedFor");
            if (recommendedFor != null && !recommendedFor.isEmpty()) {
                documentText.append("Recommended For: ");
                for (int i = 0; i < recommendedFor.size(); i++) {
                    documentText.append(recommendedFor.get(i).get("name"));
                    if (i < recommendedFor.size() - 1) {
                        documentText.append(", ");
                    }
                }
                documentText.append("\n");
            }

            // Create a document with the book information
            Document document = Document.from(documentText.toString());

            // Split the document into segments
            DocumentSplitter splitter = DocumentSplitters.recursive(100, 0);
            List<TextSegment> segments = splitter.split(document);

            // Create embeddings for each segment and store them
            for (TextSegment segment : segments) {
                Embedding embedding = embeddingModel.embed(segment).content();
                embeddingStore.add(embedding, segment);
            }
        }
    }

    public List<ChatMessage> chat(String sessionId, String message, String context) {
        // Update the current context for this session
        if (context != null && !context.isEmpty()) {
            currentContext.put(sessionId, context);
        }

        // Get or create chat memory for this session
        ChatMemory chatMemory = chatMemories.computeIfAbsent(sessionId, 
            id -> MessageWindowChatMemory.builder().maxMessages(10).build());

        // Create the chat model
        ChatLanguageModel chatModel = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("llama3")
            .temperature(0.7)
            .build();

        // Create the AI service
        BookAssistant assistant = AiServices.builder(BookAssistant.class)
            .chatLanguageModel(chatModel)
            .chatMemoryProvider(id -> chatMemory)
            .build();

        // Get the response from the assistant
        String response = assistant.chat(sessionId, message);

        // Create a list of messages for the UI
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("user", message));
        messages.add(new ChatMessage("bot", response));

        return messages;
    }

    public List<String> getConversationStarters(String context) {
        List<String> starters = new ArrayList<>();

        if (context == null || context.isEmpty() || context.equals("home")) {
            // Default conversation starters for the home page
            starters.add("What books do you recommend for me?");
            starters.add("Show me books with Science Fiction theme");
            starters.add("What are the most popular books?");
        } else if (context.startsWith("book:")) {
            // Context-aware starters for a specific book
            String bookUri = context.substring(5);
            try {
                Map<String, Object> book = rdfService.getBookDetails(bookUri);
                String title = (String) book.get("title");

                starters.add("Tell me more about \"" + title + "\"");

                // Get themes for this book
                List<Map<String, String>> themes = (List<Map<String, String>>) book.get("themes");
                if (themes != null && !themes.isEmpty()) {
                    String themeName = themes.get(0).get("name");
                    starters.add("Show me other " + themeName + " books");
                }

                // Get reading level for this book
                Map<String, String> readingLevel = (Map<String, String>) book.get("readingLevel");
                if (readingLevel != null) {
                    starters.add("What other " + readingLevel.get("name") + " level books do you recommend?");
                }
            } catch (Exception e) {
                // Fallback to default starters if there's an error
                starters.add("What books do you recommend for me?");
                starters.add("Show me books with Science Fiction theme");
                starters.add("What are the most popular books?");
            }
        } else if (context.equals("books")) {
            // Context-aware starters for the books list page
            starters.add("What is a book that I am most likely to enjoy from this list?");
            starters.add("Which book has the highest reading level?");
            starters.add("Show me books by theme");
        }

        return starters;
    }

    public List<Map<String, Object>> searchBooksByThemeAndAuthor(String theme, String author) {
        List<Map<String, Object>> results = new ArrayList<>();

        // Get all books
        List<Map<String, Object>> allBooks = rdfService.getAllBooks();

        // Filter by theme and/or author
        for (Map<String, Object> book : allBooks) {
            boolean matchesTheme = false;
            boolean matchesAuthor = false;

            // Check if the book matches the theme
            if (theme == null || theme.isEmpty()) {
                matchesTheme = true;
            } else {
                List<Map<String, String>> themes = (List<Map<String, String>>) book.get("themes");
                if (themes != null) {
                    for (Map<String, String> t : themes) {
                        if (t.get("name").toLowerCase().contains(theme.toLowerCase())) {
                            matchesTheme = true;
                            break;
                        }
                    }
                }
            }

            // Check if the book matches the author
            // Note: In our current model, we don't have author information
            // This is a placeholder for future enhancement
            if (author == null || author.isEmpty()) {
                matchesAuthor = true;
            } else {
                // For now, we'll just check if the title contains the author name
                // This is not accurate but serves as a placeholder
                String title = (String) book.get("title");
                if (title.toLowerCase().contains(author.toLowerCase())) {
                    matchesAuthor = true;
                }
            }

            // Add the book to results if it matches both criteria
            if (matchesTheme && matchesAuthor) {
                results.add(book);
            }
        }

        return results;
    }
}
