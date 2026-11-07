package br.edu.lab2.carros.api;

import br.edu.lab2.carros.api.ApiDtos.AgentEditRequest;
import br.edu.lab2.carros.api.ApiDtos.AssessmentRequest;
import br.edu.lab2.carros.api.ApiDtos.BankOption;
import br.edu.lab2.carros.api.ApiDtos.ContractExecutionRequest;
import br.edu.lab2.carros.api.ApiDtos.ContractResponse;
import br.edu.lab2.carros.api.ApiDtos.RentalResponse;
import br.edu.lab2.carros.security.AuthPrincipal;
import br.edu.lab2.carros.service.RentalService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/requests")
public class AgentController {
    private final RentalService rentals;

    public AgentController(RentalService rentals) { this.rentals = rentals; }

    @GetMapping
    public List<RentalResponse> list(@AuthenticationPrincipal AuthPrincipal principal) { return rentals.allRequests(principal); }

    @GetMapping("/banks")
    public List<BankOption> banks(@AuthenticationPrincipal AuthPrincipal principal) { return rentals.bankAgents(principal); }

    @PatchMapping("/{id}/start-analysis")
    public RentalResponse startAnalysis(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return rentals.startAnalysis(principal, id);
    }

    @PutMapping("/{id}")
    public RentalResponse edit(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                               @Valid @RequestBody AgentEditRequest request) {
        return rentals.editByAgent(principal, id, request);
    }

    @PostMapping("/{id}/assessment")
    public RentalResponse assess(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                 @Valid @RequestBody AssessmentRequest request) {
        return rentals.assess(principal, id, request);
    }

    @PostMapping("/{id}/execute")
    public ContractResponse execute(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                    @Valid @RequestBody ContractExecutionRequest request) {
        return rentals.executeContract(principal, id, request);
    }
}
