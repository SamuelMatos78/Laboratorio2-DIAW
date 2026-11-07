package br.edu.lab2.carros.api;

import br.edu.lab2.carros.api.ApiDtos.ChatRequest;
import br.edu.lab2.carros.api.ApiDtos.ChatResponse;
import br.edu.lab2.carros.api.ApiDtos.AssistantStatusResponse;
import br.edu.lab2.carros.security.AuthPrincipal;
import br.edu.lab2.carros.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) { this.assistant = assistant; }

    @GetMapping("/status")
    public AssistantStatusResponse status() { return assistant.status(); }

    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody ChatRequest request) {
        return assistant.chat(principal, request);
    }
}
