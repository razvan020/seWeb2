package com.example.bookrecommendation.controller;

import com.example.bookrecommendation.service.RdfService;
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
    public String showUploadForm() {
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
    public String showVisualization(Model model) {
        model.addAttribute("rdfContent", rdfService.modelToString());
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