package com.example.bookrecommendation.controller;

import com.example.bookrecommendation.service.ChatbotService;
import com.example.bookrecommendation.service.RdfService;
import com.example.bookrecommendation.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

@Controller
public class BookController {

    private final RdfService rdfService;
    private final UserService userService;
    private final ChatbotService chatbotService;

    @Autowired
    public BookController(RdfService rdfService, UserService userService, ChatbotService chatbotService) {
        this.rdfService = rdfService;
        this.userService = userService;
        this.chatbotService = chatbotService;
    }

    @GetMapping("/books")
    public String listBooks(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            String currentUser = (String) session.getAttribute("currentUser");
            List<Map<String, Object>> books;

            if (currentUser != null) {
                books = rdfService.getRecommendedBooksForUser(currentUser);
                model.addAttribute("currentUser", currentUser);
                model.addAttribute("userPreferences", userService.getUserPreferences(currentUser));
            } else {
                books = rdfService.getAllBooks();
            }

            model.addAttribute("books", books);
            return "books";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to load books: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/books/{bookId}")
    public String viewBook(@PathVariable String bookId, Model model, HttpSession session) {
        // Construct the full URI from the ID
        String bookUri = "http://example.org/book/" + bookId;
        Map<String, Object> book = rdfService.getBookDetails(bookUri);
        model.addAttribute("book", book);

        // Add the current user to the model
        String currentUser = (String) session.getAttribute("currentUser");
        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
        }

        return "book-details";
    }

    @GetMapping("/add-book")
    public String showAddBookForm(Model model, HttpSession session) {
        model.addAttribute("themes", rdfService.getAllThemes());
        model.addAttribute("readingLevels", rdfService.getAllReadingLevels());

        // Add the current user to the model
        String currentUser = (String) session.getAttribute("currentUser");
        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
        }

        return "add-book";
    }

    @PostMapping("/select-user")
    public String selectUser(@RequestParam String username, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            session.setAttribute("currentUser", username);
            redirectAttributes.addFlashAttribute("message", "Switched to user: " + username);
            return "redirect:/books";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to switch user: " + e.getMessage());
            return "redirect:/books";
        }
    }

    @GetMapping("/clear-user")
    public String clearUser(HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute("currentUser");
        redirectAttributes.addFlashAttribute("message", "User preferences cleared");
        return "redirect:/books";
    }

    @PostMapping("/add-book")
    public String addBook(@RequestParam String title,
                          @RequestParam List<String> themes,
                          @RequestParam String readingLevel,
                          @RequestParam(required = false) String description,
                          @RequestParam(required = false) String author,
                          RedirectAttributes redirectAttributes) {
        try {
            rdfService.addBook(title, themes, readingLevel, description, author);

            // Refresh the vector database after adding a book
            chatbotService.refreshVectorDatabase();

            redirectAttributes.addFlashAttribute("message", "Book added successfully: " + title);
            return "redirect:/books";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add book: " + e.getMessage());
            return "redirect:/add-book";
        }
    }

    @GetMapping("/edit-book/{bookId}")
    public String showEditBookForm(@PathVariable String bookId, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            // Construct the full URI from the ID
            String bookUri = "http://example.org/book/" + bookId;
            Map<String, Object> book = rdfService.getBookDetails(bookUri);

            // Add the id property to the book object
            book.put("id", bookId);

            // Extract theme URIs for easier selection in the template
            List<String> bookThemeUris = new java.util.ArrayList<>();
            if (book.get("themes") != null) {
                List<Map<String, String>> themes = (List<Map<String, String>>) book.get("themes");
                for (Map<String, String> theme : themes) {
                    bookThemeUris.add(theme.get("uri"));
                }
            }
            model.addAttribute("bookThemeUris", bookThemeUris);

            // Add the current user to the model
            String currentUser = (String) session.getAttribute("currentUser");
            if (currentUser != null) {
                model.addAttribute("currentUser", currentUser);
            }

            model.addAttribute("book", book);
            model.addAttribute("themes", rdfService.getAllThemes());
            model.addAttribute("readingLevels", rdfService.getAllReadingLevels());
            return "edit-book";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to load book for editing: " + e.getMessage());
            return "redirect:/books";
        }
    }

    @PostMapping("/edit-book/{bookId}")
    public String updateBook(@PathVariable String bookId,
                             @RequestParam String title,
                             @RequestParam List<String> themes,
                             @RequestParam String readingLevel,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) String author,
                             RedirectAttributes redirectAttributes) {
        try {
            String bookUri = "http://example.org/book/" + bookId;
            rdfService.updateBook(bookUri, title, themes, readingLevel, description, author);

            // Refresh the vector database after updating a book
            chatbotService.refreshVectorDatabase();

            redirectAttributes.addFlashAttribute("message", "Book updated successfully: " + title);
            return "redirect:/books/" + bookId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update book: " + e.getMessage());
            return "redirect:/edit-book/" + bookId;
        }
    }
}
