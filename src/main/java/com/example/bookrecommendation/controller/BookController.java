package com.example.bookrecommendation.controller;

import com.example.bookrecommendation.service.RdfService;
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

    @Autowired
    public BookController(RdfService rdfService) {
        this.rdfService = rdfService;
    }

    @GetMapping("/books")
    public String listBooks(Model model, RedirectAttributes redirectAttributes) {
        try {
            List<Map<String, Object>> books = rdfService.getAllBooks();
            model.addAttribute("books", books);
            return "books";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to load books: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/books/{bookId}")
    public String viewBook(@PathVariable String bookId, Model model) {
        // Construct the full URI from the ID
        String bookUri = "http://example.org/book/" + bookId;
        Map<String, Object> book = rdfService.getBookDetails(bookUri);
        model.addAttribute("book", book);
        return "book-details";
    }

    @GetMapping("/add-book")
    public String showAddBookForm(Model model) {
        model.addAttribute("themes", rdfService.getAllThemes());
        model.addAttribute("readingLevels", rdfService.getAllReadingLevels());
        return "add-book";
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
            redirectAttributes.addFlashAttribute("message", "Book added successfully: " + title);
            return "redirect:/books";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add book: " + e.getMessage());
            return "redirect:/add-book";
        }
    }

    @GetMapping("/edit-book/{bookUri}")
    public String showEditBookForm(@PathVariable String bookUri, Model model, RedirectAttributes redirectAttributes) {
        try {
            // URL decode the bookUri
            String decodedUri = java.net.URLDecoder.decode(bookUri, "UTF-8");
            Map<String, Object> book = rdfService.getBookDetails(decodedUri);
            model.addAttribute("book", book);
            model.addAttribute("themes", rdfService.getAllThemes());
            model.addAttribute("readingLevels", rdfService.getAllReadingLevels());
            return "edit-book";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to load book for editing: " + e.getMessage());
            return "redirect:/books";
        }
    }

    @PostMapping("/edit-book/{bookUri}")
    public String updateBook(@PathVariable String bookUri,
                             @RequestParam String title,
                             @RequestParam List<String> themes,
                             @RequestParam String readingLevel,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) String author,
                             RedirectAttributes redirectAttributes) throws UnsupportedEncodingException {
        String decodedUri = null;
        try {
            // URL decode the bookUri
            decodedUri = java.net.URLDecoder.decode(bookUri, "UTF-8");
            rdfService.updateBook(decodedUri, title, themes, readingLevel, description, author);
            redirectAttributes.addFlashAttribute("message", "Book updated successfully: " + title);
            // Re-encode the URI for the redirect
            String encodedUri = java.net.URLEncoder.encode(decodedUri, "UTF-8");
            return "redirect:/books/" + encodedUri;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update book: " + e.getMessage());
            // Re-encode the URI for the redirect
            String encodedUri = java.net.URLEncoder.encode(decodedUri, "UTF-8");
            return "redirect:/edit-book/" + encodedUri;
        }
    }
}
