package br.edu.lab2.carros.api;

import br.edu.lab2.carros.api.ApiDtos.ClientResponse;
import br.edu.lab2.carros.api.ApiDtos.ClientUpdateRequest;
import br.edu.lab2.carros.security.AuthPrincipal;
import br.edu.lab2.carros.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/profile")
public class ClientController {
    private final ClientService clients;

    public ClientController(ClientService clients) { this.clients = clients; }

    @GetMapping
    public ClientResponse get(@AuthenticationPrincipal AuthPrincipal principal) { return clients.get(principal); }

    @PutMapping
    public ClientResponse update(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ClientUpdateRequest request) {
        return clients.update(principal, request);
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal AuthPrincipal principal) {
        clients.deactivate(principal);
        return ResponseEntity.noContent().build();
    }
}
