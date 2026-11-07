package br.edu.lab2.carros.service;

import br.edu.lab2.carros.api.ApiDtos.ClientResponse;
import br.edu.lab2.carros.api.ApiDtos.ClientUpdateRequest;
import br.edu.lab2.carros.api.ApiDtos.EmploymentInput;
import br.edu.lab2.carros.api.ApiDtos.EmploymentResponse;
import br.edu.lab2.carros.domain.ClientProfile;
import br.edu.lab2.carros.domain.Employment;
import br.edu.lab2.carros.domain.UserAccount;
import br.edu.lab2.carros.domain.UserRole;
import br.edu.lab2.carros.repository.ClientProfileRepository;
import br.edu.lab2.carros.repository.UserAccountRepository;
import br.edu.lab2.carros.security.AuthPrincipal;
import br.edu.lab2.carros.security.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {
    private final ClientProfileRepository clients;
    private final UserAccountRepository users;
    private final SessionRegistry sessions;

    public ClientService(ClientProfileRepository clients, UserAccountRepository users, SessionRegistry sessions) {
        this.clients = clients;
        this.users = users;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public ClientResponse get(AuthPrincipal principal) {
        ensureClient(principal);
        return toResponse(requireClient(principal.userId()));
    }

    @Transactional
    public ClientResponse update(AuthPrincipal principal, ClientUpdateRequest request) {
        ensureClient(principal);
        ClientProfile profile = requireClient(principal.userId());
        String cpf = normalizeCpf(request.cpf());
        if (clients.existsByCpfAndIdNot(cpf, profile.id)) throw ApiException.conflict("O CPF informado pertence a outro cadastro.");
        if (clients.existsByRgAndIdNot(request.rg().trim(), profile.id)) throw ApiException.conflict("O RG informado pertence a outro cadastro.");
        profile.rg = request.rg().trim();
        profile.cpf = cpf;
        profile.name = request.name().trim();
        profile.address = request.address().trim();
        profile.profession = request.profession().trim();
        List<Employment> employments = request.employments() == null ? List.of() : request.employments().stream()
                .map(input -> new Employment(input.employer().trim(), input.income())).toList();
        profile.replaceEmployments(employments);
        return toResponse(clients.save(profile));
    }

    @Transactional
    public void deactivate(AuthPrincipal principal) {
        ensureClient(principal);
        UserAccount account = users.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("Conta não encontrada."));
        account.enabled = false;
        users.save(account);
        sessions.revokeUser(account.id);
    }

    @Transactional(readOnly = true)
    public ClientProfile requireClient(UUID userId) {
        return clients.findByUserAccount_Id(userId).orElseThrow(() -> ApiException.notFound("Cadastro de cliente não encontrado."));
    }

    private ClientResponse toResponse(ClientProfile profile) {
        List<EmploymentResponse> employments = profile.employments.stream()
                .map(item -> new EmploymentResponse(item.id, item.employer, item.income)).toList();
        return new ClientResponse(profile.id, profile.userAccount.username, profile.rg, profile.cpf, profile.name,
                profile.address, profile.profession, employments);
    }

    private String digits(String value) { return value.replaceAll("\\D", ""); }

    private String normalizeCpf(String value) {
        String cpf = digits(value);
        if (cpf.length() != 11) throw ApiException.badRequest("O CPF deve conter 11 dígitos.");
        return cpf;
    }

    private void ensureClient(AuthPrincipal principal) {
        if (principal.role() != UserRole.CLIENT) throw ApiException.forbidden("Esta operação é exclusiva para clientes.");
    }
}
