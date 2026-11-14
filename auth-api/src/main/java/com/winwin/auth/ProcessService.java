package com.winwin.auth;

import com.winwin.auth.Dtos.TransformRequest;
import com.winwin.auth.Dtos.TransformResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@Service
public class ProcessService {

    private final RestClient restClient;
    private final ProcessingLogRepository logs;

    public ProcessService(ProcessingLogRepository logs,
                          @Value("${app.data-api.url}") String dataApiUrl,
                          @Value("${app.internal-token}") String internalToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(dataApiUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .requestFactory(factory)
                .build();
        this.logs = logs;
    }

    public String process(UUID userId, String text) {
        TransformResponse response;
        try {
            response = restClient.post()
                    .uri("/api/transform")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TransformRequest(text))
                    .retrieve()
                    .body(TransformResponse.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Processing service is unavailable");
        }
        if (response == null || response.result() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from processing service");
        }
        logs.save(new ProcessingLog(userId, text, response.result()));
        return response.result();
    }
}