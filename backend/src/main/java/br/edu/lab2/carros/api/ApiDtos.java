package br.edu.lab2.carros.api;

import br.edu.lab2.carros.domain.AgentType;
import br.edu.lab2.carros.domain.OwnerType;
import br.edu.lab2.carros.domain.RequestStatus;
import br.edu.lab2.carros.domain.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}

    public record EmploymentInput(
            @NotBlank @Size(max = 140) String employer,
            @NotNull @DecimalMin("0.01") BigDecimal income) {}

    public record RegisterClientRequest(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 30) String rg,
            @NotBlank @Size(min = 11, max = 14) String cpf,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 240) String address,
            @NotBlank @Size(max = 100) String profession,
            @Size(max = 3) List<@Valid EmploymentInput> employments) {}

    public record RegisterAgentRequest(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull AgentType type,
            @NotBlank @Size(max = 140) String entityName,
            @NotBlank String inviteCode) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record AuthResponse(String token, UUID userId, String username, UserRole role, String displayName) {}
    public record CurrentUserResponse(UUID userId, String username, UserRole role, String displayName) {}

    public record ClientUpdateRequest(
            @NotBlank @Size(max = 30) String rg,
            @NotBlank @Size(min = 11, max = 14) String cpf,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 240) String address,
            @NotBlank @Size(max = 100) String profession,
            @Size(max = 3) List<@Valid EmploymentInput> employments) {}

    public record EmploymentResponse(UUID id, String employer, BigDecimal income) {}
    public record ClientResponse(UUID id, String username, String rg, String cpf, String name, String address,
                                 String profession, List<EmploymentResponse> employments) {}

    public record AvailableCarResponse(UUID id, String make, String model, int year, String category,
                                       String transmission, String fuel, BigDecimal dailyPrice, String imageUrl) {}

    public record ChatMessageInput(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 2000) String content) {}

    public record ChatRequest(@NotEmpty @Size(max = 12) List<@Valid ChatMessageInput> messages) {}
    public record ChatResponse(String reply) {}
    public record AssistantStatusResponse(boolean configured, String model) {}

    public record RentalCreateRequest(
            @NotNull UUID carId,
            @NotNull @FutureOrPresent LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 1000) String clientNotes) {}

    public record AgentEditRequest(
            @NotNull @FutureOrPresent LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 1000) String clientNotes) {}

    public record AssessmentRequest(@NotBlank @Size(max = 1000) String opinion, @NotNull Boolean approved) {}
    public record ContractExecutionRequest(
            @NotNull OwnerType ownerType,
            @NotBlank @Size(max = 140) String ownerName,
            @DecimalMin("0.01") BigDecimal creditAmount,
            UUID bankAgentId) {}

    public record RentalResponse(UUID id, RequestStatus status, Instant submittedAt, Instant updatedAt,
                                 LocalDate startDate, LocalDate endDate, String clientNotes, String financialOpinion,
                                 UUID clientId, String clientName, String username, String clientRg, String clientCpf,
                                 String clientAddress, String profession, List<EmploymentResponse> employments,
                                 UUID carId, String registration, int year, String make, String model, String plate,
                                 String category, String transmission, String fuel, BigDecimal dailyPrice, String imageUrl,
                                 UUID evaluatedByAgentId, String evaluatedBy) {}
    public record ContractResponse(UUID id, UUID requestId, Instant executedAt, OwnerType ownerType, String ownerName,
                                   String executedBy, BigDecimal creditAmount, String bankName) {}
    public record BankOption(UUID id, String entityName) {}
}
