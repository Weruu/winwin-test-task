package com.winwin.auth;

import com.winwin.auth.Dtos.ProcessRequest;
import com.winwin.auth.Dtos.ProcessResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProcessController {

    private final ProcessService processService;

    public ProcessController(ProcessService processService) {
        this.processService = processService;
    }

    @PostMapping("/process")
    public ProcessResponse process(@AuthenticationPrincipal UUID userId,
                                   @Valid @RequestBody ProcessRequest request) {
        return new ProcessResponse(processService.process(userId, request.text()));
    }
}