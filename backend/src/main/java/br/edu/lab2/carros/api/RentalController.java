package br.edu.lab2.carros.api;

import br.edu.lab2.carros.api.ApiDtos.RentalCreateRequest;
import br.edu.lab2.carros.api.ApiDtos.RentalResponse;
import br.edu.lab2.carros.security.AuthPrincipal;
import br.edu.lab2.carros.service.RentalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
public class RentalController {
    private final RentalService rentals;

    public RentalController(RentalService rentals) { this.rentals = rentals; }

    @GetMapping
    public List<RentalResponse> list(@AuthenticationPrincipal AuthPrincipal principal) { return rentals.clientRequests(principal); }

    @PostMapping
    public ResponseEntity<RentalResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody RentalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rentals.create(principal, request));
    }

    @PutMapping("/{id}")
    public RentalResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                 @Valid @RequestBody RentalCreateRequest request) {
        return rentals.updateByClient(principal, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        rentals.cancelByClient(principal, id);
        return ResponseEntity.noContent().build();
    }
}
