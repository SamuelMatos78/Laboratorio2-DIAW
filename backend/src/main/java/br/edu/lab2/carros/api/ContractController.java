package br.edu.lab2.carros.api;

import br.edu.lab2.carros.api.ApiDtos.ContractResponse;
import br.edu.lab2.carros.security.AuthPrincipal;
import br.edu.lab2.carros.service.RentalService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final RentalService rentals;

    public ContractController(RentalService rentals) { this.rentals = rentals; }

    @GetMapping("/requests/{requestId}")
    public ContractResponse getForRequest(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID requestId) {
        return rentals.contractForRequest(principal, requestId);
    }
}
