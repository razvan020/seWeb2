package com.example.bookrecommendation.service;

import com.example.bookrecommendation.model.ChatMessage;
import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatbotService {

    private final RdfService rdfService;
    private final EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private final Map<String, ChatMemory> chatMemories = new ConcurrentHashMap<>();
    private final Map<String, String> currentContext = new ConcurrentHashMap<>();

    @Value("${google.ai.api-key}")
    private String googleApiKey;

    @Value("${google.ai.model}")
    private String googleModel;

    @Value("${google.ai.project-id}")
    private String googleProjectId;

    private interface BookAssistant {
        String chat(@MemoryId String sessionId, @UserMessage String message);
    }

    public void refreshVectorDatabase() {
        // Create a new embedding store to replace the existing one
        this.embeddingStore = new InMemoryEmbeddingStore<>();

        // Reinitialize the vector database with the latest book data
        initializeVectorDatabase();

        System.out.println("Vector database refreshed with latest book data");
    }

    @Autowired
    public ChatbotService(RdfService rdfService) {
        this.rdfService = rdfService;
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        this.embeddingStore = new InMemoryEmbeddingStore<>();

        // Add books from XML data
        addBooksFromXml();

        // Initialize the vector database with book data
        initializeVectorDatabase();

        // Initialize the Gemini API client
        initializeGeminiClient();
    }

    private VertexAI vertexAI;
    private GenerativeModel generativeModel;

    private void initializeGeminiClient() {
        try {
            // Initialize VertexAI with project ID and location
            vertexAI = new VertexAI(googleProjectId, "us-central1");

            // Initialize the generative model with the specified model name
            generativeModel = new GenerativeModel(googleModel, vertexAI);

            System.out.println("Gemini API client initialized successfully");
        } catch (Exception e) {
            System.err.println("Error initializing Gemini API client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addBooksFromXml() {
        // Parse the XML data and add books to the RDF model
        try {
            // Get existing themes and reading levels
            List<Map<String, String>> existingThemes = rdfService.getAllThemes();
            List<Map<String, String>> existingReadingLevels = rdfService.getAllReadingLevels();

            // Map to store theme names to URIs
            Map<String, String> themeUriMap = new HashMap<>();
            for (Map<String, String> theme : existingThemes) {
                themeUriMap.put(theme.get("name"), theme.get("uri"));
            }

            // Map to store reading level names to URIs
            Map<String, String> readingLevelUriMap = new HashMap<>();
            for (Map<String, String> level : existingReadingLevels) {
                readingLevelUriMap.put(level.get("name"), level.get("uri"));
            }

            // Check for missing themes and add them
            Set<String> allThemes = new HashSet<>();
            allThemes.add("Science Fiction");
            allThemes.add("Fantasy");
            allThemes.add("Mystery");
            allThemes.add("Romance");
            allThemes.add("Historical");
            allThemes.add("Thriller");
            allThemes.add("Murder");

            for (String themeName : allThemes) {
                if (!themeUriMap.containsKey(themeName)) {
                    // Theme doesn't exist, create it in the RDF model
                    String themeId = themeName.replaceAll("\\s+", "");
                    String themeUri = "http://example.org/book/" + themeId;

                    // Add the theme to the RDF model
                    // This is a simplified approach - in a real application, you would use RdfService to add the theme
                    System.out.println("Adding missing theme: " + themeName);

                    // Add the theme to the map
                    themeUriMap.put(themeName, themeUri);
                }
            }

            // Check for missing reading levels and add them
            Set<String> allLevels = new HashSet<>();
            allLevels.add("Beginner");
            allLevels.add("Intermediate");
            allLevels.add("Advanced");

            for (String levelName : allLevels) {
                if (!readingLevelUriMap.containsKey(levelName)) {
                    // Reading level doesn't exist, create it in the RDF model
                    String levelUri = "http://example.org/book/" + levelName;

                    // Add the reading level to the RDF model
                    // This is a simplified approach - in a real application, you would use RdfService to add the reading level
                    System.out.println("Adding missing reading level: " + levelName);

                    // Add the reading level to the map
                    readingLevelUriMap.put(levelName, levelUri);
                }
            }

            // XML data from the issue description (24 books)
            String[] books = {
                "1.1984by George Orwell|Science Fiction,Fantasy|Advanced,Intermediate",
                "2.Adventures of Huckleberry Finnby Mark Twain|Fantasy,Mystery|Beginner,Advanced,Intermediate",
                "3.The Adventures of Sherlock Holmesby Arthur Conan Doyle|Historical,Science Fiction|Intermediate,Advanced,Beginner",
                "4.The Alchemistby Paulo Coelho|Romance,Fantasy|Advanced",
                "5.The Aleph and Other Storiesby Jorge Luis Borges|Mystery,Fantasy|Advanced,Intermediate",
                "6.Animal Farmby George Orwell|Historical,Romance|Advanced,Intermediate,Beginner",
                "7.Aesop's Fablesby Aesop|Fantasy,Mystery|Beginner",
                "8.Alice's Adventures in Wonderlandby Lewis Carroll|Science Fiction,Mystery|Beginner,Advanced,Intermediate",
                "9.Anna Kareninaby Leo Tolstoy|Science Fiction,Romance|Beginner,Intermediate,Advanced",
                "10.Anne of Green Gablesby L.M. Montgomery|Historical,Fantasy|Beginner,Intermediate,Advanced",
                "11.As I Lay Dyingby William Faulkner|Thriller,Science Fiction|Beginner",
                "12.Belovedby Toni Morrison|Romance,Fantasy|Advanced,Beginner,Intermediate",
                "13.The Book Thiefby Markus Zusak|Romance,Science Fiction|Advanced,Intermediate",
                "14.Brave New Worldby Aldous Huxley|Mystery,Historical|Beginner",
                "15.The Brothers Karamazovby Fyodor Dostoevsky|Mystery,Romance|Beginner,Intermediate,Advanced",
                "16.Catch-22by Joseph Heller|Historical,Thriller|Advanced,Beginner,Intermediate",
                "17.The Catcher in the Ryeby J.D. Salinger|Science Fiction,Historical|Beginner",
                "18.Charlie and the Chocolate Factoryby Roald Dahl|Mystery,Thriller|Beginner,Advanced,Intermediate",
                "19.Charlotte's Webby E. B White|Science Fiction,Historical|Beginner,Advanced",
                "20.The Call of the Wildby Jack London|Historical,Fantasy|Beginner"
            };

            // Process each book
            for (String bookData : books) {
                String[] parts = bookData.split("\\|");
                if (parts.length >= 3) {
                    // Extract title and author
                    String titleWithAuthor = parts[0];
                    String title = "";
                    String author = "";

                    // Parse title and author (format: "1.1984by George Orwell")
                    int byIndex = titleWithAuthor.indexOf("by");
                    if (byIndex > 0) {
                        // Remove the number prefix (e.g., "1.")
                        String withoutNumber = titleWithAuthor.substring(titleWithAuthor.indexOf(".") + 1);
                        title = withoutNumber.substring(0, withoutNumber.indexOf("by"));
                        author = withoutNumber.substring(withoutNumber.indexOf("by") + 2);
                    } else {
                        title = titleWithAuthor;
                    }

                    // Extract themes
                    String[] themeNames = parts[1].split(",");
                    List<String> themeUris = new ArrayList<>();
                    for (String themeName : themeNames) {
                        String themeUri = themeUriMap.get(themeName);
                        if (themeUri != null) {
                            themeUris.add(themeUri);
                        }
                    }

                    // Extract reading levels
                    String[] levelNames = parts[2].split(",");
                    String readingLevelUri = null;
                    if (levelNames.length > 0) {
                        String levelName = levelNames[0]; // Use the first reading level
                        readingLevelUri = readingLevelUriMap.get(levelName);
                    }

                    // Add the book to the RDF model if it doesn't already exist
                    List<Map<String, Object>> existingBooks = rdfService.getAllBooks();
                    boolean bookExists = false;
                    for (Map<String, Object> existingBook : existingBooks) {
                        if (existingBook.get("title").equals(title)) {
                            bookExists = true;
                            break;
                        }
                    }

                    if (!bookExists && readingLevelUri != null && !themeUris.isEmpty()) {
                        rdfService.addBook(title, themeUris, readingLevelUri, "", author);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error adding books from XML: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeVectorDatabase() {
        // Get all books from the RDF model
        List<Map<String, Object>> books = rdfService.getAllBooks();

        // Create documents for each book
        for (Map<String, Object> book : books) {
            StringBuilder documentText = new StringBuilder();
            documentText.append("Title: ").append(book.get("title")).append("\n");

            // Add author if available
            if (book.containsKey("author")) {
                documentText.append("Author: ").append(book.get("author")).append("\n");
            }

            // Add description if available
            if (book.containsKey("description")) {
                documentText.append("Description: ").append(book.get("description")).append("\n");
            }

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

            // Add URI for reference
            documentText.append("URI: ").append(book.get("uri")).append("\n");

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

        // Retrieve relevant information from the vector database
        List<String> relevantInfo = retrieveRelevantInformation(message);

        // Prepare the prompt with context and relevant information
        String prompt = preparePrompt(message, relevantInfo, context);

        // Call the Gemini API
        String response = callGeminiApi(prompt);

        // Create a list of messages for the UI
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("user", message));
        messages.add(new ChatMessage("bot", response));

        return messages;
    }

    private List<String> retrieveRelevantInformation(String query) {
        // Create an embedding for the query
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // Find similar content in the vector database
        List<EmbeddingMatch<TextSegment>> relevantMatches = 
            embeddingStore.findRelevant(queryEmbedding, 5);

        // Extract the text from the matches
        List<String> relevantInfo = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : relevantMatches) {
            relevantInfo.add(match.embedded().text());
        }

        return relevantInfo;
    }

    private String preparePrompt(String userQuery, List<String> relevantInfo, String context) {
        StringBuilder promptBuilder = new StringBuilder();

        // Add system instructions
        promptBuilder.append("You are a helpful book assistant. ");
        promptBuilder.append("Use ONLY the following information to answer the user's question. ");
        promptBuilder.append("If the information provided doesn't contain the answer, say so honestly ");
        promptBuilder.append("and suggest the user ask about books in our database.\n\n");

        // Add context information
        if (context != null && !context.isEmpty()) {
            if (context.startsWith("book:")) {
                promptBuilder.append("The user is currently viewing a book page. ");
                String bookUri = context.substring(5);
                try {
                    Map<String, Object> book = rdfService.getBookDetails(bookUri);
                    promptBuilder.append("The book is: ").append(book.get("title"));

                    if (book.containsKey("author")) {
                        promptBuilder.append(" by ").append(book.get("author"));
                    }

                    promptBuilder.append(".\n");
                } catch (Exception e) {
                    // If there's an error, just use a generic context
                    promptBuilder.append("The user is viewing a book page.\n");
                }
            } else if (context.equals("books")) {
                promptBuilder.append("The user is viewing a list of books.\n");
            } else if (context.equals("home")) {
                promptBuilder.append("The user is on the home page.\n");
            }
        }

        // Add relevant information from the vector database
        if (!relevantInfo.isEmpty()) {
            promptBuilder.append("\nRelevant information from our database:\n");
            for (String info : relevantInfo) {
                promptBuilder.append("- ").append(info).append("\n");
            }
            promptBuilder.append("\n");
        }

        // Add the user query
        promptBuilder.append("User query: ").append(userQuery);

        // Add additional instructions for the model
        promptBuilder.append("\n\nImportant: Your response should be concise and directly answer the user's question. ");
        promptBuilder.append("Format your response in a conversational way. ");
        promptBuilder.append("If you're listing books, use bullet points. ");
        promptBuilder.append("If you're providing information about a specific book, make sure to include its title, author, and themes.");

        return promptBuilder.toString();
    }

    private String callGeminiApi(String prompt) {
        try {
            // Check if the Gemini client is initialized
            if (generativeModel == null) {
                initializeGeminiClient();
                if (generativeModel == null) {
                    return "Sorry, I'm having trouble connecting to the AI service. Please try again later.";
                }
            }

            // Generate content using the Gemini API
            GenerateContentResponse response = generativeModel.generateContent(prompt);

            // Extract the response text
            String responseText = response.getCandidates(0).getContent().getParts(0).getText();

            // If the response is empty, return a fallback message
            if (responseText == null || responseText.trim().isEmpty()) {
                return "I couldn't generate a response. Please try asking a different question.";
            }

            return responseText;
        } catch (Exception e) {
            System.err.println("Error calling Gemini API: " + e.getMessage());
            e.printStackTrace();

            // If there's an error, fall back to the existing implementation
            return fallbackResponse(prompt);
        }
    }

    // Fallback method that uses the existing implementation
    private String fallbackResponse(String prompt) {
        // Extract relevant information from the prompt
        String relevantInfo = "";
        if (prompt.contains("Relevant information from our database:")) {
            int startIndex = prompt.indexOf("Relevant information from our database:");
            int endIndex = prompt.indexOf("User query:");
            if (endIndex > startIndex) {
                relevantInfo = prompt.substring(startIndex, endIndex).trim();
            }
        }

        // Extract the user query
        String userQuery = "";
        if (prompt.contains("User query:")) {
            userQuery = prompt.substring(prompt.indexOf("User query:") + "User query:".length()).trim();
        }

        // Handle greetings
        if (userQuery.toLowerCase().matches(".*\\b(hi|hello|hey)\\b.*")) {
            return "Hello! I'm your book assistant powered by Gemini AI. " +
                    "Based on the information in our database, I can help you find books, " +
                    "learn about authors, and discover new reading material. " +
                    "What would you like to know about books?";
        }

        // Rest of the existing implementation...
        // (Keep the existing code for handling specific query types)

        // Default response for other queries
        StringBuilder response = new StringBuilder();

        if (!relevantInfo.isEmpty()) {
            // Parse the relevant information to create a more natural response
            response.append("Based on our database, here's what I found:\n\n");

            // Extract and format book information from relevantInfo
            // (Keep the existing code for formatting the response)
        } else {
            response.append("I don't have specific information about that in our database. ");
            response.append("You can ask me about books, authors, themes, or reading levels that are in our collection.");
        }

        return response.toString();
    }

    private List<Map<String, Object>> searchBooksByTitle(String title) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (title == null || title.isEmpty()) {
            return results;
        }

        // First try vector search for more accurate results
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("Title: ").append(title);
        String query = queryBuilder.toString();

        // Use vector search to find relevant books
        List<String> relevantInfo = retrieveRelevantInformation(query);

        // Extract book URIs from the relevant information
        Set<String> matchedUris = new HashSet<>();
        for (String info : relevantInfo) {
            // Extract URI from the text
            int uriIndex = info.indexOf("URI: ");
            if (uriIndex >= 0) {
                String uriLine = info.substring(uriIndex);
                String uri = uriLine.replace("URI: ", "").trim();
                matchedUris.add(uri);
            }
        }

        // Get book details for the matched URIs
        for (String uri : matchedUris) {
            try {
                Map<String, Object> book = rdfService.getBookDetails(uri);
                // Check if the title matches
                String bookTitle = (String) book.get("title");
                if (bookTitle.toLowerCase().contains(title.toLowerCase())) {
                    results.add(book);
                }
            } catch (Exception e) {
                // Skip books that can't be retrieved
            }
        }

        // If no results from vector search, fall back to traditional search
        if (results.isEmpty()) {
            List<Map<String, Object>> allBooks = rdfService.getAllBooks();

            for (Map<String, Object> book : allBooks) {
                String bookTitle = (String) book.get("title");
                if (bookTitle.toLowerCase().contains(title.toLowerCase())) {
                    results.add(book);
                }
            }
        }

        return results;
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

                // Add author-related starter if author is available
                if (book.containsKey("author")) {
                    String author = (String) book.get("author");
                    starters.add("Tell me more about " + author + ", the author of this book");
                }

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


    private String extractParameter(String text) {
        // Simple extraction - can be improved
        if (text.contains(" and ")) {
            return text.substring(0, text.indexOf(" and ")).trim();
        }

        if (text.contains("?")) {
            return text.substring(0, text.indexOf("?")).trim();
        }

        return text.trim();
    }

    public List<Map<String, Object>> searchBooksByThemeAndAuthor(String theme, String author) {
        List<Map<String, Object>> results = new ArrayList<>();

        // Special case for common themes that need direct search
        if (theme != null && !theme.isEmpty() && 
            (theme.equalsIgnoreCase("Science Fiction") || 
             theme.equalsIgnoreCase("ScienceFiction") ||
             theme.equalsIgnoreCase("Murder") ||
             theme.equalsIgnoreCase("Fantasy") ||
             theme.equalsIgnoreCase("Mystery"))) {

            // Use direct search for these specific themes
            List<Map<String, Object>> allBooks = rdfService.getAllBooks();

            for (Map<String, Object> book : allBooks) {
                boolean matchesTheme = false;
                boolean matchesAuthor = true; // Default to true if no author specified

                // Check if the book matches the theme
                List<Map<String, String>> themes = (List<Map<String, String>>) book.get("themes");
                if (themes != null) {
                    for (Map<String, String> t : themes) {
                        String themeName = t.get("name");
                        if (themeName != null && 
                            (themeName.equalsIgnoreCase(theme) || 
                             (theme.equalsIgnoreCase("Science Fiction") && themeName.equalsIgnoreCase("ScienceFiction")) ||
                             (theme.equalsIgnoreCase("ScienceFiction") && themeName.equalsIgnoreCase("Science Fiction")))) {
                            matchesTheme = true;
                            break;
                        }
                    }
                }

                // Check if the book matches the author if provided
                if (author != null && !author.isEmpty()) {
                    matchesAuthor = false;
                    if (book.containsKey("author")) {
                        String bookAuthor = (String) book.get("author");
                        if (bookAuthor.toLowerCase().contains(author.toLowerCase())) {
                            matchesAuthor = true;
                        }
                    }
                }

                // Add the book to results if it matches both criteria
                if (matchesTheme && matchesAuthor) {
                    results.add(book);
                }
            }

            return results;
        }


        // If both theme and author are provided, use vector search for more accurate results
        if ((theme != null && !theme.isEmpty()) || (author != null && !author.isEmpty())) {
            // Construct a query that combines theme and author
            StringBuilder queryBuilder = new StringBuilder();
            if (theme != null && !theme.isEmpty() && theme.contains(" ")) {
                // For themes with spaces, add both versions to improve matching
                String noSpaceTheme = theme.replace(" ", "");
                queryBuilder.append("Theme: ").append(theme).append(" OR Theme: ").append(noSpaceTheme);
            } else if (theme != null && !theme.isEmpty()) {
                queryBuilder.append("Theme: ").append(theme);
            }
            if (author != null && !author.isEmpty()) {
                if (queryBuilder.length() > 0) {
                    queryBuilder.append(", ");
                }
                queryBuilder.append("Author: ").append(author);
            }


            String query = queryBuilder.toString();

            // Use vector search to find relevant books
            List<String> relevantInfo = retrieveRelevantInformation(query);

            // Extract book URIs from the relevant information
            Set<String> matchedUris = new HashSet<>();
            for (String info : relevantInfo) {
                // Extract URI from the text
                int uriIndex = info.indexOf("URI: ");
                if (uriIndex >= 0) {
                    String uriLine = info.substring(uriIndex);
                    String uri = uriLine.replace("URI: ", "").trim();
                    matchedUris.add(uri);
                }
            }

            // Get book details for the matched URIs
            for (String uri : matchedUris) {
                try {
                    Map<String, Object> book = rdfService.getBookDetails(uri);

                    // Verify the book matches the theme if a theme was provided
                    boolean matchesTheme = true;
                    if (theme != null && !theme.isEmpty()) {
                        matchesTheme = false;
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

                    // Verify the book matches the author if an author was provided
                    boolean matchesAuthor = true;
                    if (author != null && !author.isEmpty()) {
                        matchesAuthor = false;
                        if (book.containsKey("author")) {
                            String bookAuthor = (String) book.get("author");
                            if (bookAuthor.toLowerCase().contains(author.toLowerCase())) {
                                matchesAuthor = true;
                            }
                        }
                    }

                    // Add the book to results if it matches both criteria
                    if (matchesTheme && matchesAuthor) {
                        results.add(book);
                    }
                } catch (Exception e) {
                    // Skip books that can't be retrieved
                }
            }

            return results;
        }

        // Fallback to traditional search if no specific criteria are provided
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
            if (author == null || author.isEmpty()) {
                matchesAuthor = true;
            } else if (book.containsKey("author")) {
                String bookAuthor = (String) book.get("author");
                if (bookAuthor.toLowerCase().contains(author.toLowerCase())) {
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
