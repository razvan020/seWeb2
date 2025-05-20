package com.example.bookrecommendation.controller;

import com.example.bookrecommendation.service.RdfService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Controller
public class RdfController {

    private final RdfService rdfService;

    @Autowired
    public RdfController(RdfService rdfService) {
        this.rdfService = rdfService;
    }

    @GetMapping("/rdf-upload")
    public String showUploadForm(Model model, HttpSession session) {
        // Add the current user to the model
        String currentUser = (String) session.getAttribute("currentUser");
        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
        }
        return "rdf-upload";
    }

    @PostMapping("/rdf-upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file, Model model) {
        try {
            String filename = rdfService.saveUploadedFile(file);
            model.addAttribute("message", "File uploaded successfully: " + filename);
            return "redirect:/rdf-visualization";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to upload file: " + e.getMessage());
            return "rdf-upload";
        }
    }

    @GetMapping("/rdf-visualization")
    public String showVisualization(Model model, HttpSession session) {
        model.addAttribute("rdfContent", rdfService.modelToString());

        // Add the current user to the model
        String currentUser = (String) session.getAttribute("currentUser");
        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
        }

        return "rdf-visualization";
    }

    @GetMapping("/api/rdf-graph-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getGraphData() {
        return ResponseEntity.ok(rdfService.getGraphData());
    }

    @GetMapping("/reset-rdf")
    public String resetToDefaultRdf() {
        rdfService.loadDefaultModel();
        return "redirect:/rdf-visualization";
    }
}
