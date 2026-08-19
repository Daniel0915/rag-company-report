package com.ismsp.chatbot.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import com.ismsp.chatbot.dto.ChatRequest;
import com.ismsp.chatbot.dto.ChatResponse;
import com.ismsp.chatbot.dto.MetadataOptions;
import com.ismsp.chatbot.dto.UploadResult;
import com.ismsp.chatbot.service.ChatService;
import com.ismsp.chatbot.service.CompanyDocIndexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class IsmsPController {

    private final CompanyDocIndexService indexService;
    private final ChatService chatService;

    public IsmsPController(CompanyDocIndexService indexService, ChatService chatService) {
        this.indexService = indexService;
        this.chatService = chatService;
    }

    @GetMapping("/api/isms-p/metadata-options")
    public MetadataOptions metadataOptions() {
        return MetadataOptions.defaults();
    }

    @PostMapping("/api/isms-p/upload")
    public UploadResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("doc_type") String docType,
            @RequestParam("domain") String domain,
            @RequestParam("year") String year
    ) throws IOException {
        File temp = File.createTempFile("isms-p-upload-", "-" + file.getOriginalFilename());
        try {
            file.transferTo(temp);
            return indexService.indexDocument(temp, file.getOriginalFilename(), docType, domain, year);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    @PostMapping("/api/isms-p/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.chat(request.question(), request.filter(), 4);
    }

    @PostMapping("/api/isms-p/delete-all")
    public Map<String, Boolean> deleteAll() {
        indexService.deleteAll();
        return Map.of("ok", true);
    }
}
