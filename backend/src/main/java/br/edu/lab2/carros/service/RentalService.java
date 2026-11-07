package br.edu.lab2.carros.service;

import br.edu.lab2.carros.api.ApiDtos.AgentEditRequest;
import br.edu.lab2.carros.api.ApiDtos.AssessmentRequest;
import br.edu.lab2.carros.api.ApiDtos.BankOption;
import br.edu.lab2.carros.api.ApiDtos.ContractExecutionRequest;
import br.edu.lab2.carros.api.ApiDtos.ContractResponse;
import br.edu.lab2.carros.api.ApiDtos.EmploymentResponse;
import br.edu.lab2.carros.api.ApiDtos.RentalCreateRequest;
import br.edu.lab2.carros.api.ApiDtos.RentalResponse;
import br.edu.lab2.carros.domain.AgentProfile;
import br.edu.lab2.carros.domain.AgentType;
import br.edu.lab2.carros.domain.Car;
import br.edu.lab2.carros.domain.ClientProfile;
import br.edu.lab2.carros.domain.CreditContract;
import br.edu.lab2.carros.domain.OwnerType;
import br.edu.lab2.carros.domain.RentalContract;
import br.edu.lab2.carros.domain.RentalRequest;
import br.edu.lab2.carros.domain.RequestStatus;
import br.edu.lab2.carros.repository.AgentProfileRepository;
import br.edu.lab2.carros.repository.CarRepository;
import br.edu.lab2.carros.repository.ClientProfileRepository;
import br.edu.lab2.carros.repository.CreditContractRepository;
import br.edu.lab2.carros.repository.RentalContractRepository;
import br.edu.lab2.carros.repository.RentalRequestRepository;
import br.edu.lab2.carros.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class RentalService {
    private static final List<RequestStatus> CLOSED_STATUSES = List.of(RequestStatus.CANCELADO, RequestStatus.REPROVADO);

    private final RentalRequestRepository requests;
    private final ClientProfileRepository clients;
    private final AgentProfileRepository agents;
    private final CarRepository cars;
    private final RentalContractRepository contracts;
    private final CreditContractRepository creditContracts;

    public RentalService(RentalRequestRepository requests, ClientProfileRepository clients, AgentProfileRepository agents,
                         CarRepository cars, RentalContractRepository contracts, CreditContractRepository creditContracts) {
        this.requests = requests;
        this.clients = clients;
        this.agents = agents;
        this.cars = cars;
        this.contracts = contracts;
        this.creditContracts = creditContracts;
    }

    @Transactional
    public RentalResponse create(AuthPrincipal principal, RentalCreateRequest input) {
        validatePeriod(input.startDate(), input.endDate());
        ClientProfile client = requireClient(principal.userId());
        Car car = requireAvailableCar(input.carId(), input.startDate(), input.endDate(), null);
        RentalRequest request = new RentalRequest(client, car, input.startDate(), input.endDate(), trimToNull(input.clientNotes()));
        return toResponse(requests.save(request));
    }

    @Transactional(readOnly = true)
    public List<RentalResponse> clientRequests(AuthPrincipal principal) {
        return requests.findAllByClientProfile_UserAccount_IdOrderBySubmittedAtDesc(principal.userId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public RentalResponse updateByClient(AuthPrincipal principal, UUID requestId, RentalCreateRequest input) {
        validatePeriod(input.startDate(), input.endDate());
        RentalRequest request = requests.findByIdAndClientProfile_UserAccount_Id(requestId, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        if (request.status != RequestStatus.PENDENTE_ANALISE) throw ApiException.conflict("O pedido só pode ser alterado enquanto aguarda análise.");
        request.car = requireAvailableCar(input.carId(), input.startDate(), input.endDate(), requestId);
        updateSchedule(request, input.startDate(), input.endDate(), input.clientNotes());
        request.updatedAt = java.time.Instant.now();
        return toResponse(requests.save(request));
    }

    @Transactional
    public void cancelByClient(AuthPrincipal principal, UUID requestId) {
        RentalRequest request = requests.findByIdAndClientProfile_UserAccount_Id(requestId, principal.userId())
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        if (request.status == RequestStatus.CONTRATO_EXECUTADO || request.status == RequestStatus.CANCELADO) {
            throw ApiException.conflict("Este pedido não pode mais ser cancelado.");
        }
        request.status = RequestStatus.CANCELADO;
        request.updatedAt = java.time.Instant.now();
        requests.save(request);
    }

    @Transactional(readOnly = true)
    public List<RentalResponse> allRequests(AuthPrincipal principal) {
        requireAgent(principal.userId());
        return requests.findAllByOrderBySubmittedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BankOption> bankAgents(AuthPrincipal principal) {
        requireAgent(principal.userId());
        return agents.findAllByTypeOrderByEntityNameAsc(AgentType.BANK).stream()
                .map(agent -> new BankOption(agent.id, agent.entityName)).toList();
    }

    @Transactional
    public RentalResponse startAnalysis(AuthPrincipal principal, UUID requestId) {
        AgentProfile agent = requireAgent(principal.userId());
        RentalRequest request = requireRequest(requestId);
        if (request.status != RequestStatus.PENDENTE_ANALISE) throw ApiException.conflict("Somente pedidos pendentes podem iniciar análise.");
        request.status = RequestStatus.EM_ANALISE;
        request.evaluatedBy = agent;
        request.updatedAt = java.time.Instant.now();
        return toResponse(requests.save(request));
    }

    @Transactional
    public RentalResponse editByAgent(AuthPrincipal principal, UUID requestId, AgentEditRequest input) {
        requireAgent(principal.userId());
        RentalRequest request = requireRequest(requestId);
        if (request.status != RequestStatus.PENDENTE_ANALISE && request.status != RequestStatus.EM_ANALISE) {
            throw ApiException.conflict("Este pedido não aceita mais alterações.");
        }
        validatePeriod(input.startDate(), input.endDate());
        Car car = cars.lockCar(request.car.id).orElseThrow(() -> ApiException.notFound("Veículo não encontrado."));
        ensureNoConflict(car.id, input.startDate(), input.endDate(), requestId);
        updateSchedule(request, input.startDate(), input.endDate(), input.clientNotes());
        request.updatedAt = java.time.Instant.now();
        return toResponse(requests.save(request));
    }

    @Transactional
    public RentalResponse assess(AuthPrincipal principal, UUID requestId, AssessmentRequest input) {
        AgentProfile agent = requireAgent(principal.userId());
        RentalRequest request = requireRequest(requestId);
        if (request.status != RequestStatus.EM_ANALISE) throw ApiException.conflict("Inicie a análise antes de registrar um parecer.");
        request.evaluatedBy = agent;
        request.financialOpinion = input.opinion().trim();
        request.status = input.approved() ? RequestStatus.AGUARDANDO_EXECUCAO : RequestStatus.REPROVADO;
        request.updatedAt = java.time.Instant.now();
        return toResponse(requests.save(request));
    }

    @Transactional
    public ContractResponse executeContract(AuthPrincipal principal, UUID requestId, ContractExecutionRequest input) {
        AgentProfile executingAgent = requireAgent(principal.userId());
        RentalRequest request = requireRequest(requestId);
        if (request.status != RequestStatus.AGUARDANDO_EXECUCAO) throw ApiException.conflict("O pedido precisa estar aprovado e aguardando execução.");
        if (contracts.findByRentalRequest_Id(requestId).isPresent()) throw ApiException.conflict("Já existe contrato para este pedido.");

        boolean hasCreditAmount = input.creditAmount() != null;
        boolean hasBankAgent = input.bankAgentId() != null;
        if (hasCreditAmount != hasBankAgent) throw ApiException.badRequest("Informe o valor do crédito e o banco concedente juntos.");
        RentalContract contract = contracts.save(new RentalContract(request, request.car, input.ownerType(), input.ownerName().trim(), executingAgent));
        if (hasCreditAmount) {
            AgentProfile bank = agents.findByIdAndType(input.bankAgentId(), AgentType.BANK)
                    .orElseThrow(() -> ApiException.badRequest("O agente informado não está cadastrado como banco."));
            creditContracts.save(new CreditContract(contract, bank, input.creditAmount()));
        }
        request.status = RequestStatus.CONTRATO_EXECUTADO;
        request.updatedAt = java.time.Instant.now();
        requests.save(request);
        return toContractResponse(contract);
    }

    @Transactional(readOnly = true)
    public ContractResponse contractForRequest(AuthPrincipal principal, UUID requestId) {
        RentalRequest request = requireRequest(requestId);
        boolean isOwner = request.clientProfile.userAccount.id.equals(principal.userId());
        boolean isAgent = agents.findByUserAccount_Id(principal.userId()).isPresent();
        if (!isOwner && !isAgent) throw ApiException.forbidden("Você não pode consultar este contrato.");
        RentalContract contract = contracts.findByRentalRequest_Id(requestId)
                .orElseThrow(() -> ApiException.notFound("Contrato ainda não encontrado."));
        return toContractResponse(contract);
    }

    private ClientProfile requireClient(UUID userId) {
        return clients.findByUserAccount_Id(userId).orElseThrow(() -> ApiException.notFound("Cadastro de cliente não encontrado."));
    }

    private AgentProfile requireAgent(UUID userId) {
        return agents.findByUserAccount_Id(userId).orElseThrow(() -> ApiException.forbidden("Esta operação é exclusiva para agentes."));
    }

    private RentalRequest requireRequest(UUID id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
    }

    private Car requireAvailableCar(UUID carId, LocalDate startDate, LocalDate endDate, UUID ignoredRequestId) {
        Car car = cars.lockAvailableCar(carId)
                .orElseThrow(() -> ApiException.conflict("Este veículo não está disponível para aluguel."));
        ensureNoConflict(car.id, startDate, endDate, ignoredRequestId);
        return car;
    }

    private void ensureNoConflict(UUID carId, LocalDate startDate, LocalDate endDate, UUID ignoredRequestId) {
        if (requests.hasConflictingRequest(carId, startDate, endDate, ignoredRequestId, CLOSED_STATUSES)) {
            throw ApiException.conflict("Este veículo já foi solicitado para o período informado.");
        }
    }

    private void updateSchedule(RentalRequest request, LocalDate startDate, LocalDate endDate, String notes) {
        request.startDate = startDate;
        request.endDate = endDate;
        request.clientNotes = trimToNull(notes);
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start.isBefore(LocalDate.now())) throw ApiException.badRequest("A data de início não pode estar no passado.");
        if (!end.isAfter(start)) throw ApiException.badRequest("A data final deve ser posterior à data inicial.");
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private RentalResponse toResponse(RentalRequest item) {
        List<EmploymentResponse> employments = item.clientProfile.employments.stream()
                .map(value -> new EmploymentResponse(value.id, value.employer, value.income)).toList();
        return new RentalResponse(item.id, item.status, item.submittedAt, item.updatedAt, item.startDate, item.endDate,
                item.clientNotes, item.financialOpinion, item.clientProfile.id, item.clientProfile.name,
                item.clientProfile.userAccount.username, item.clientProfile.rg, item.clientProfile.cpf,
                item.clientProfile.address, item.clientProfile.profession, employments,
                item.car.id, item.car.registration, item.car.year,
                item.car.make, item.car.model, item.car.plate, item.car.category, item.car.transmission,
                item.car.fuel, item.car.dailyPrice, item.car.imageUrl,
                item.evaluatedBy == null ? null : item.evaluatedBy.id,
                item.evaluatedBy == null ? null : item.evaluatedBy.entityName);
    }

    private ContractResponse toContractResponse(RentalContract contract) {
        CreditContract credit = creditContracts.findByRentalContract_Id(contract.id).orElse(null);
        return new ContractResponse(contract.id, contract.rentalRequest.id, contract.executedAt, contract.ownerType,
                contract.ownerName, contract.executedBy.entityName, credit == null ? null : credit.amount,
                credit == null ? null : credit.bankAgent.entityName);
    }
}
