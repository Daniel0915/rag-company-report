package com.ismsp.chatbot.controller;

import java.util.List;

import com.ismsp.chatbot.dto.AdminDocumentDto;
import com.ismsp.chatbot.service.AdminDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
public class AdminDocumentController {

    private final AdminDocumentService adminDocumentService;

    @GetMapping
    public List<AdminDocumentDto> list() {
        return adminDocumentService.listDocuments();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminDocumentDto upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("corpCode") String corpCode,
            @RequestParam("title") String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "docDate", required = false) String docDate
    ) {
        return adminDocumentService.uploadDocument(file, corpCode, title, category, description, docDate);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        adminDocumentService.deleteDocument(id);
    }
}
