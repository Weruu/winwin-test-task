package com.winwin.data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransformController {

    public record TransformRequest(@NotBlank @Size(max = 10000) String text) {}

    public record TransformResponse(String result) {}

    @PostMapping("/transform")
    public TransformResponse transform(@Valid @RequestBody TransformRequest request) {
        String result = new StringBuilder(request.text()).reverse().toString().toUpperCase();
        return new TransformResponse(result);
    }
}