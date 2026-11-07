package br.edu.lab2.carros.service;

import br.edu.lab2.carros.api.ApiDtos.AuthResponse;
import br.edu.lab2.carros.api.ApiDtos.CurrentUserResponse;
import br.edu.lab2.carros.api.ApiDtos.EmploymentInput;
import br.edu.lab2.carros.api.ApiDtos.LoginRequest;
import br.edu.lab2.carros.api.ApiDtos.RegisterAgentRequest;
import br.edu.lab2.carros.api.ApiDtos.RegisterClientRequest;
import br.edu.lab2.carros.domain.AgentProfile;
import br.edu.lab2.carros.domain.AgentType;
import br.edu.lab2.carros.domain.ClientProfile;
import br.edu.lab2.carros.domain.Employment;
import br.edu.lab2.carros.domain.UserAccount;
import br.edu.lab2.carros.domain.UserRole;
import br.edu.lab2.carros.repository.AgentProfileRepository;
import br.edu.lab2.carros.repository.ClientProfileRepository;
import br.edu.lab2.carros.repository.UserAccountRepository;
import br.edu.lab2.carros.security.SessionRegistry;
import br.edu.lab2.carros.security.AuthPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final ClientProfileRepository clients;
    private final AgentProfileRepository agents;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessions;
    private final String agentInviteCode;

    public AuthService(UserAccountRepository users, ClientProfileRepository clients, AgentProfileRepository agents,
                       PasswordEncoder passwordEncoder, SessionRegistry sessions,
                       @Value("${app.auth.agent-invite-code:}") String agentInviteCode) {
        this.users = users;
        this.clients = clients;
        this.agents = agents;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.agentInviteCode = agentInviteCode;
    }

    @Transactional
    public AuthResponse registerClient(RegisterClientRequest request) {
        String username = normalizeUsername(request.username());
        ensureUsernameAvailable(username);
        String cpf = normalizeCpf(request.cpf());
        String rg = request.rg().trim();
        if (clients.existsByCpf(cpf)) throw ApiException.conflict("Já existe um cliente cadastrado com esse CPF.");
        if (clients.existsByRg(rg)) throw ApiException.conflict("Já existe um cliente cadastrado com esse RG.");

        UserAccount account = users.save(new UserAccount(username, passwordEncoder.encode(request.password()), UserRole.CLIENT));
        ClientProfile profile = new ClientProfile(account, rg, cpf, request.name().trim(), request.address().trim(), request.profession().trim());
        profile.replaceEmployments(mapEmployments(request.employments()));
        clients.save(profile);
        return response(account, profile.name);
    }

    @Transactional
    public AuthResponse registerAgent(RegisterAgentRequest request) {
        if (agentInviteCode.isBlank() || !constantTimeEquals(agentInviteCode, request.inviteCode())) {
            throw ApiException.forbidden("Código de convite de agente inválido ou não configurado.");
        }
        String username = normalizeUsername(request.username());
        ensureUsernameAvailable(username);
        UserRole role = request.type() == AgentType.BANK ? UserRole.BANK_AGENT : UserRole.COMPANY_AGENT;
        UserAccount account = users.save(new UserAccount(username, passwordEncoder.encode(request.password()), role));
        agents.save(new AgentProfile(account, request.type(), request.entityName().trim()));
        return response(account, request.entityName().trim());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAccount account = users.findByUsernameIgnoreCase(normalizeUsername(request.username()))
                .filter(user -> user.enabled && passwordEncoder.matches(request.password(), user.passwordHash))
                .orElseThrow(() -> ApiException.unauthorized("Usuário ou senha inválidos."));
        String displayName = account.role == UserRole.CLIENT
                ? clients.findByUserAccount_Id(account.id).map(client -> client.name).orElse(account.username)
                : agents.findByUserAccount_Id(account.id).map(agent -> agent.entityName).orElse(account.username);
        return response(account, displayName);
    }

    public AuthResponse createSession(AuthResponse identity) {
        UserAccount account = users.findById(identity.userId()).orElseThrow(() -> ApiException.unauthorized("Conta não encontrada."));
        if (!account.enabled) throw ApiException.unauthorized("Esta conta está desativada.");
        return new AuthResponse(sessions.create(account), identity.userId(), identity.username(), identity.role(), identity.displayName());
    }

    public void logout(String token) {
        sessions.revoke(token);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse current(AuthPrincipal principal) {
        UserAccount account = users.findById(principal.userId()).filter(user -> user.enabled)
                .orElseThrow(() -> ApiException.unauthorized("Sessão inválida ou conta desativada."));
        String displayName = account.role == UserRole.CLIENT
                ? clients.findByUserAccount_Id(account.id).map(client -> client.name).orElse(account.username)
                : agents.findByUserAccount_Id(account.id).map(agent -> agent.entityName).orElse(account.username);
        return new CurrentUserResponse(account.id, account.username, account.role, displayName);
    }

    private void ensureUsernameAvailable(String username) {
        if (users.findByUsernameIgnoreCase(username).isPresent()) throw ApiException.conflict("Este usuário já está cadastrado.");
    }

    private AuthResponse response(UserAccount account, String displayName) {
        return new AuthResponse(null, account.id, account.username, account.role, displayName);
    }

    private List<Employment> mapEmployments(List<EmploymentInput> values) {
        return values == null ? List.of() : values.stream().map(item -> new Employment(item.employer().trim(), item.income())).toList();
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }

    private String digits(String value) {
        return value.replaceAll("\\D", "");
    }

    private String normalizeCpf(String value) {
        String cpf = digits(value);
        if (cpf.length() != 11) throw ApiException.badRequest("O CPF deve conter 11 dígitos.");
        return cpf;
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
