package com.example.bookrecommendation.service;

import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.algorithms.layout.CircleLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.visualization.VisualizationImageServer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class RdfService {

    private static final String UPLOAD_DIR = "src/main/resources/uploads/";
    private static final String DEFAULT_RDF_PATH = "src/main/resources/rdf/book_recommendation.rdf";

    private Model currentModel;

    public RdfService() {
        // Create upload directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }

        // Load the default RDF model
        loadDefaultModel();
    }

    public void loadDefaultModel() {
        try {
            currentModel = ModelFactory.createDefaultModel();
            InputStream in = new FileInputStream(DEFAULT_RDF_PATH);
            currentModel.read(in, null);
            in.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default RDF model", e);
        }
    }

    public String saveUploadedFile(MultipartFile file) {
        try {
            // Generate a unique filename
            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path targetLocation = Paths.get(UPLOAD_DIR + filename);

            // Copy the file to the target location
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Load the uploaded RDF model
            currentModel = ModelFactory.createDefaultModel();
            InputStream in = new FileInputStream(targetLocation.toFile());

            try {
                // Try to determine the format from the file extension
                String fileExtension = getFileExtension(file.getOriginalFilename());
                String lang = null;

                if ("rdf".equalsIgnoreCase(fileExtension) || "xml".equalsIgnoreCase(fileExtension)) {
                    lang = "RDF/XML";
                } else if ("ttl".equalsIgnoreCase(fileExtension)) {
                    lang = "TURTLE";
                } else if ("n3".equalsIgnoreCase(fileExtension)) {
                    lang = "N3";
                } else if ("nt".equalsIgnoreCase(fileExtension)) {
                    lang = "N-TRIPLE";
                } else if ("jsonld".equalsIgnoreCase(fileExtension)) {
                    lang = "JSON-LD";
                }

                // Read the model with the specified language if available
                if (lang != null) {
                    currentModel.read(in, null, lang);
                } else {
                    // Try to read as RDF/XML by default
                    currentModel.read(in, null);
                }
            } catch (Exception e) {
                // If reading fails, try again with different formats
                in.close();
                in = new FileInputStream(targetLocation.toFile());

                try {
                    // Try TURTLE
                    currentModel.read(in, null, "TURTLE");
                } catch (Exception e2) {
                    in.close();
                    in = new FileInputStream(targetLocation.toFile());

                    try {
                        // Try N3
                        currentModel.read(in, null, "N3");
                    } catch (Exception e3) {
                        in.close();
                        in = new FileInputStream(targetLocation.toFile());

                        // Try N-TRIPLE as a last resort
                        currentModel.read(in, null, "N-TRIPLE");
                    }
                }
            }

            in.close();
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store or parse file: " + e.getMessage(), e);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    public Model getCurrentModel() {
        return currentModel;
    }

    public String modelToString() {
        StringWriter out = new StringWriter();
        currentModel.write(out, "RDF/XML-ABBREV");
        return out.toString();
    }

    public Map<String, Object> getGraphData() {
        Map<String, Object> graphData = new HashMap<>();
        List<Map<String, String>> nodes = new ArrayList<>();
        List<Map<String, String>> edges = new ArrayList<>();

        // Create a Jung graph
        Graph<Resource, Statement> jungGraph = new DirectedSparseGraph<>();

        // Process nodes (subjects and objects)
        Set<String> processedNodes = new HashSet<>();
        Map<String, Resource> resourceMap = new HashMap<>();

        // First pass: add all resources to the Jung graph
        StmtIterator stmtIterator = currentModel.listStatements();
        while (stmtIterator.hasNext()) {
            Statement stmt = stmtIterator.nextStatement();
            Resource subject = stmt.getSubject();

            // Add subject to Jung graph if not already added
            if (!jungGraph.containsVertex(subject)) {
                jungGraph.addVertex(subject);
            }

            // Process object if it's a resource (not a literal)
            RDFNode object = stmt.getObject();
            if (object.isResource()) {
                Resource objectResource = object.asResource();

                // Add object to Jung graph if not already added
                if (!jungGraph.containsVertex(objectResource)) {
                    jungGraph.addVertex(objectResource);
                }

                // Add edge to Jung graph
                jungGraph.addEdge(stmt, subject, objectResource);
            }
        }

        // Create a layout for the Jung graph
        Layout<Resource, Statement> layout = new CircleLayout<>(jungGraph);

        // Second pass: convert Jung graph to vis.js format
        for (Resource vertex : jungGraph.getVertices()) {
            String vertexId = vertex.toString();
            if (!processedNodes.contains(vertexId)) {
                Map<String, String> node = new HashMap<>();
                node.put("id", vertexId);
                node.put("label", getLabel(vertex));
                node.put("type", getType(vertex));
                nodes.add(node);
                processedNodes.add(vertexId);
                resourceMap.put(vertexId, vertex);
            }
        }

        for (Statement edge : jungGraph.getEdges()) {
            Resource source = edge.getSubject();
            RDFNode target = edge.getObject();

            if (target.isResource()) {
                Map<String, String> edgeMap = new HashMap<>();
                edgeMap.put("source", source.toString());
                edgeMap.put("target", target.asResource().toString());
                edgeMap.put("label", edge.getPredicate().getLocalName());
                edges.add(edgeMap);
            }
        }

        graphData.put("nodes", nodes);
        graphData.put("edges", edges);
        return graphData;
    }

    private String getLabel(Resource resource) {
        // Try to get rdfs:label
        StmtIterator labelStmts = currentModel.listStatements(resource, RDFS.label, (RDFNode) null);
        if (labelStmts.hasNext()) {
            return labelStmts.nextStatement().getObject().toString();
        }

        // If no label, use the local name or URI
        return resource.getLocalName() != null && !resource.getLocalName().isEmpty() 
                ? resource.getLocalName() 
                : resource.getURI();
    }

    private String getType(Resource resource) {
        // Try to get rdf:type
        StmtIterator typeStmts = currentModel.listStatements(resource, RDF.type, (RDFNode) null);
        if (typeStmts.hasNext()) {
            Resource typeResource = typeStmts.nextStatement().getObject().asResource();
            return typeResource.getLocalName();
        }

        // If no type, try to infer from URI
        String uri = resource.getURI();
        if (uri != null) {
            if (uri.contains("/book/")) {
                return "Book";
            } else if (uri.contains("/user/")) {
                return "User";
            }
        }

        return "Resource";
    }

    // Methods for Exercise 3 and 4

    public void addBook(String title, List<String> themeUris, String readingLevelUri) {
        // Create a unique URI for the new book
        String bookUri = "http://example.org/book/" + title.replaceAll("\\s+", "");

        // Create the book resource
        Resource bookResource = currentModel.createResource(bookUri);

        // Add rdf:type
        bookResource.addProperty(RDF.type, currentModel.createResource("http://example.org/book/Book"));

        // Add rdfs:label
        bookResource.addProperty(RDFS.label, title);

        // Add themes
        Property hasThemeProperty = currentModel.createProperty("http://example.org/book/hasTheme");
        for (String themeUri : themeUris) {
            Resource themeResource = currentModel.createResource(themeUri);
            bookResource.addProperty(hasThemeProperty, themeResource);
        }

        // Add reading level
        Property hasReadingLevelProperty = currentModel.createProperty("http://example.org/book/hasReadingLevel");
        Resource readingLevelResource = currentModel.createResource(readingLevelUri);
        bookResource.addProperty(hasReadingLevelProperty, readingLevelResource);

        // Save the updated model
        saveModel();
    }

    public void updateBook(String bookUri, String title, List<String> themeUris, String readingLevelUri) {
        // Get the book resource
        Resource bookResource = currentModel.getResource(bookUri);
        if (bookResource == null) {
            throw new RuntimeException("Book not found: " + bookUri);
        }

        // Update rdfs:label
        Statement labelStmt = bookResource.getProperty(RDFS.label);
        if (labelStmt != null) {
            bookResource.removeAll(RDFS.label);
        }
        bookResource.addProperty(RDFS.label, title);

        // Update themes
        Property hasThemeProperty = currentModel.createProperty("http://example.org/book/hasTheme");
        bookResource.removeAll(hasThemeProperty);
        for (String themeUri : themeUris) {
            Resource themeResource = currentModel.createResource(themeUri);
            bookResource.addProperty(hasThemeProperty, themeResource);
        }

        // Update reading level
        Property hasReadingLevelProperty = currentModel.createProperty("http://example.org/book/hasReadingLevel");
        bookResource.removeAll(hasReadingLevelProperty);
        Resource readingLevelResource = currentModel.createResource(readingLevelUri);
        bookResource.addProperty(hasReadingLevelProperty, readingLevelResource);

        // Save the updated model
        saveModel();
    }

    private void saveModel() {
        try {
            FileOutputStream out = new FileOutputStream(DEFAULT_RDF_PATH);
            currentModel.write(out, "RDF/XML-ABBREV");
            out.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save RDF model", e);
        }
    }

    public List<Map<String, Object>> getAllBooks() {
        List<Map<String, Object>> books = new ArrayList<>();

        // Get all resources of type Book
        ResIterator bookIterator = currentModel.listResourcesWithProperty(
                RDF.type, 
                currentModel.createResource("http://example.org/book/Book"));

        while (bookIterator.hasNext()) {
            Resource bookResource = bookIterator.next();
            Map<String, Object> book = new HashMap<>();

            // Get book URI
            book.put("uri", bookResource.getURI());

            // Get book title
            Statement labelStmt = bookResource.getProperty(RDFS.label);
            if (labelStmt != null) {
                book.put("title", labelStmt.getString());
            } else {
                book.put("title", bookResource.getLocalName());
            }

            // Get book themes
            List<Map<String, String>> themes = new ArrayList<>();
            Property hasThemeProperty = currentModel.createProperty("http://example.org/book/hasTheme");
            StmtIterator themeIterator = bookResource.listProperties(hasThemeProperty);
            while (themeIterator.hasNext()) {
                Statement themeStmt = themeIterator.next();
                Resource themeResource = themeStmt.getObject().asResource();
                Map<String, String> theme = new HashMap<>();
                theme.put("uri", themeResource.getURI());

                Statement themeLabelStmt = themeResource.getProperty(RDFS.label);
                if (themeLabelStmt != null) {
                    theme.put("name", themeLabelStmt.getString());
                } else {
                    theme.put("name", themeResource.getLocalName());
                }

                themes.add(theme);
            }
            book.put("themes", themes);

            // Get book reading level
            Property hasReadingLevelProperty = currentModel.createProperty("http://example.org/book/hasReadingLevel");
            Statement readingLevelStmt = bookResource.getProperty(hasReadingLevelProperty);
            if (readingLevelStmt != null) {
                Resource readingLevelResource = readingLevelStmt.getObject().asResource();
                Map<String, String> readingLevel = new HashMap<>();
                readingLevel.put("uri", readingLevelResource.getURI());

                Statement readingLevelLabelStmt = readingLevelResource.getProperty(RDFS.label);
                if (readingLevelLabelStmt != null) {
                    readingLevel.put("name", readingLevelLabelStmt.getString());
                } else {
                    readingLevel.put("name", readingLevelResource.getLocalName());
                }

                book.put("readingLevel", readingLevel);
            }

            books.add(book);
        }

        return books;
    }

    public Map<String, Object> getBookDetails(String bookUri) {
        Resource bookResource = currentModel.getResource(bookUri);
        if (bookResource == null) {
            throw new RuntimeException("Book not found: " + bookUri);
        }

        Map<String, Object> book = new HashMap<>();

        // Get book URI
        book.put("uri", bookResource.getURI());

        // Get book title
        Statement labelStmt = bookResource.getProperty(RDFS.label);
        if (labelStmt != null) {
            book.put("title", labelStmt.getString());
        } else {
            book.put("title", bookResource.getLocalName());
        }

        // Get book themes
        List<Map<String, String>> themes = new ArrayList<>();
        Property hasThemeProperty = currentModel.createProperty("http://example.org/book/hasTheme");
        StmtIterator themeIterator = bookResource.listProperties(hasThemeProperty);
        while (themeIterator.hasNext()) {
            Statement themeStmt = themeIterator.next();
            Resource themeResource = themeStmt.getObject().asResource();
            Map<String, String> theme = new HashMap<>();
            theme.put("uri", themeResource.getURI());

            Statement themeLabelStmt = themeResource.getProperty(RDFS.label);
            if (themeLabelStmt != null) {
                theme.put("name", themeLabelStmt.getString());
            } else {
                theme.put("name", themeResource.getLocalName());
            }

            themes.add(theme);
        }
        book.put("themes", themes);

        // Get book reading level
        Property hasReadingLevelProperty = currentModel.createProperty("http://example.org/book/hasReadingLevel");
        Statement readingLevelStmt = bookResource.getProperty(hasReadingLevelProperty);
        if (readingLevelStmt != null) {
            Resource readingLevelResource = readingLevelStmt.getObject().asResource();
            Map<String, String> readingLevel = new HashMap<>();
            readingLevel.put("uri", readingLevelResource.getURI());

            Statement readingLevelLabelStmt = readingLevelResource.getProperty(RDFS.label);
            if (readingLevelLabelStmt != null) {
                readingLevel.put("name", readingLevelLabelStmt.getString());
            } else {
                readingLevel.put("name", readingLevelResource.getLocalName());
            }

            book.put("readingLevel", readingLevel);
        }

        // Get recommended for users
        List<Map<String, String>> recommendedFor = new ArrayList<>();
        Property isRecommendedForProperty = currentModel.createProperty("http://example.org/book/isRecommendedFor");
        StmtIterator userIterator = bookResource.listProperties(isRecommendedForProperty);
        while (userIterator.hasNext()) {
            Statement userStmt = userIterator.next();
            Resource userResource = userStmt.getObject().asResource();
            Map<String, String> user = new HashMap<>();
            user.put("uri", userResource.getURI());

            Statement userLabelStmt = userResource.getProperty(RDFS.label);
            if (userLabelStmt != null) {
                user.put("name", userLabelStmt.getString());
            } else {
                user.put("name", userResource.getLocalName());
            }

            recommendedFor.add(user);
        }
        book.put("recommendedFor", recommendedFor);

        return book;
    }

    public List<Map<String, String>> getAllThemes() {
        List<Map<String, String>> themes = new ArrayList<>();

        // Get all resources of type Theme
        ResIterator themeIterator = currentModel.listResourcesWithProperty(
                RDF.type, 
                currentModel.createResource("http://example.org/book/Theme"));

        while (themeIterator.hasNext()) {
            Resource themeResource = themeIterator.next();
            Map<String, String> theme = new HashMap<>();

            theme.put("uri", themeResource.getURI());

            Statement labelStmt = themeResource.getProperty(RDFS.label);
            if (labelStmt != null) {
                theme.put("name", labelStmt.getString());
            } else {
                theme.put("name", themeResource.getLocalName());
            }

            themes.add(theme);
        }

        return themes;
    }

    public List<Map<String, String>> getAllReadingLevels() {
        List<Map<String, String>> readingLevels = new ArrayList<>();

        // Get all resources of type ReadingLevel
        ResIterator readingLevelIterator = currentModel.listResourcesWithProperty(
                RDF.type, 
                currentModel.createResource("http://example.org/book/ReadingLevel"));

        while (readingLevelIterator.hasNext()) {
            Resource readingLevelResource = readingLevelIterator.next();
            Map<String, String> readingLevel = new HashMap<>();

            readingLevel.put("uri", readingLevelResource.getURI());

            Statement labelStmt = readingLevelResource.getProperty(RDFS.label);
            if (labelStmt != null) {
                readingLevel.put("name", labelStmt.getString());
            } else {
                readingLevel.put("name", readingLevelResource.getLocalName());
            }

            readingLevels.add(readingLevel);
        }

        return readingLevels;
    }
}
