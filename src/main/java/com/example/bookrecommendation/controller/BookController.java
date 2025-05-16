package com.example.bookrecommendation.controller;

import com.example.bookrecommendation.service.RdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    public String listBooks(Model model) {
        List<Map<String, Object>> books = rdfService.getAllBooks();
        model.addAttribute("books", books);
        return "books";
    }

    @GetMapping("/books/{bookUri}")
    public String viewBook(@PathVariable String bookUri, Model model) {
        try {
            Map<String, Object> book = rdfService.getBookDetails(bookUri);
            model.addAttribute("book", book);
            return "book-details";
        } catch (Exception e) {
            return "redirect:/books";
        }
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
                          RedirectAttributes redirectAttributes) {
        try {
            rdfService.addBook(title, themes, readingLevel);
            redirectAttributes.addFlashAttribute("message", "Book added successfully: " + title);
            return "redirect:/books";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add book: " + e.getMessage());
            return "redirect:/add-book";
        }
    }

    @GetMapping("/edit-book/{bookUri}")
    public String showEditBookForm(@PathVariable String bookUri, Model model) {
        try {
            Map<String, Object> book = rdfService.getBookDetails(bookUri);
            model.addAttribute("book", book);
            model.addAttribute("themes", rdfService.getAllThemes());
            model.addAttribute("readingLevels", rdfService.getAllReadingLevels());
            return "edit-book";
        } catch (Exception e) {
            return "redirect:/books";
        }
    }

    @PostMapping("/edit-book/{bookUri}")
    public String updateBook(@PathVariable String bookUri,
                             @RequestParam String title,
                             @RequestParam List<String> themes,
                             @RequestParam String readingLevel,
                             RedirectAttributes redirectAttributes) {
        try {
            rdfService.updateBook(bookUri, title, themes, readingLevel);
            redirectAttributes.addFlashAttribute("message", "Book updated successfully: " + title);
            return "redirect:/books/" + bookUri;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update book: " + e.getMessage());
            return "redirect:/edit-book/" + bookUri;
        }
    }
}