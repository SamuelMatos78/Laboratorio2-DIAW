package br.edu.lab2.carros.service;

import br.edu.lab2.carros.api.ApiDtos.ChatMessageInput;
import br.edu.lab2.carros.api.ApiDtos.ChatRequest;
import br.edu.lab2.carros.api.ApiDtos.ChatResponse;
import br.edu.lab2.carros.api.ApiDtos.AssistantStatusResponse;
import br.edu.lab2.carros.domain.Car;
import br.edu.lab2.carros.repository.CarRepository;
import br.edu.lab2.carros.security.AuthPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLHandshakeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AssistantService {
    private static final int MAX_TOTAL_CHARS = 8000;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final String SYSTEM_PROMPT = """
            Você é o assistente virtual da Rota, uma plataforma brasileira de aluguel de carros.
            Responda sempre em português brasileiro, com clareza e cordialidade. Ajude com dúvidas
            sobre o fluxo do sistema, tipos de veículos, preços diários e como acompanhar pedidos.
            A disponibilidade depende das datas e deve ser confirmada na busca de aluguel do site.
            Não afirme que fez reservas, alterou pedidos, aprovou crédito ou executou contratos.
            Não solicite nem revele senhas, tokens, CPF, RG ou outros dados pessoais. Para decisões
            sobre um pedido específico, oriente o cliente a consultar a página Meus pedidos ou falar
            com um agente humano. Os preços abaixo são diárias de referência em reais.
            """;

    private final String endpointUrl;
    private final CarRepository cars;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AssistantService(CarRepository cars,
                            @Value("${app.ai.openai.base-url}") String baseUrl,
                            @Value("${app.ai.openai.api-key:}") String apiKey,
                            @Value("${app.ai.openai.model}") String model,
                            @Value("${app.ai.openai.max-tokens:900}") int maxTokens) {
        this.endpointUrl = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        this.cars = cars;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = Math.clamp(maxTokens, 128, 4096);
    }

    public ChatResponse chat(AuthPrincipal principal, ChatRequest input) {
        if (principal == null) throw ApiException.unauthorized("Entre na sua conta para conversar com o assistente.");
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.serviceUnavailable("O assistente ainda não está configurado. Defina OPENAI_API_KEY no .env e reinicie o ambiente.");
        }
        List<ChatMessageInput> history = input.messages();
        if (!"user".equals(history.getLast().role())) {
            throw ApiException.badRequest("A última mensagem da conversa deve ser sua pergunta.");
        }
        int totalChars = history.stream().mapToInt(message -> message.content().length()).sum();
        if (totalChars > MAX_TOTAL_CHARS) {
            throw ApiException.badRequest("A conversa está muito longa. Inicie uma nova conversa e tente novamente.");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPromptWithFleet()));
        history.forEach(message -> messages.add(Map.of("role", message.role(), "content", message.content().trim())));

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", maxTokens
        );

        HttpResponse<String> response;
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(request)))
                    .build();
            response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw ApiException.badGateway("Não foi possível alcançar o serviço de conversa. Verifique sua conexão e tente novamente.");
        } catch (IOException exception) {
            throw connectionFailure(exception);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw ApiException.badGateway("Não foi possível alcançar o serviço de conversa. Verifique a URL configurada e tente novamente.");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw upstreamFailure(response.statusCode(), response.body());
        }
        try {
            return new ChatResponse(extractReply(jsonMapper.readTree(response.body())));
        } catch (JacksonException exception) {
            throw ApiException.badGateway("O serviço de conversa retornou uma resposta inválida. Tente novamente em instantes.");
        }
    }

    public AssistantStatusResponse status() {
        return new AssistantStatusResponse(apiKey != null && !apiKey.isBlank(), model);
    }

    private ApiException connectionFailure(IOException exception) {
        if (hasCause(exception, UnknownHostException.class)) {
            return ApiException.badGateway("Não foi possível localizar o provedor de IA. Verifique sua internet e OPENAI_BASE_URL.");
        }
        if (hasCause(exception, HttpConnectTimeoutException.class) || hasCause(exception, HttpTimeoutException.class)) {
            return ApiException.badGateway("O provedor de IA demorou para responder. Tente novamente em instantes.");
        }
        if (hasCause(exception, SSLHandshakeException.class)) {
            return ApiException.badGateway("A conexão segura com o provedor de IA falhou. Verifique a data do sistema e a rede.");
        }
        if (hasCause(exception, ConnectException.class)) {
            return ApiException.badGateway("A conexão com o provedor de IA foi recusada. Verifique OPENAI_BASE_URL e sua rede.");
        }
        return ApiException.badGateway("Não foi possível conectar ao provedor de IA. Verifique sua conexão e OPENAI_BASE_URL.");
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }

    private ApiException upstreamFailure(int status, String responseBody) {
        String details = upstreamErrorDetails(responseBody);
        if (status == 401 || status == 403) {
            return ApiException.serviceUnavailable("A chave do provedor de conversa é inválida ou não tem acesso ao modelo configurado.");
        }
        if (status == 429) {
            return ApiException.serviceUnavailable("O serviço de conversa está com muitas solicitações. Tente novamente em instantes.");
        }
        if (status == 400 || status == 422) {
            return ApiException.badGateway("O provedor recusou a solicitação (HTTP " + status + details + "). Confira o modelo e os parâmetros aceitos.");
        }
        if (status == 404) {
            return ApiException.badGateway("O endpoint do provedor não foi encontrado (HTTP 404). Confira OPENAI_BASE_URL.");
        }
        return ApiException.badGateway("O provedor de conversa respondeu com HTTP " + status + details + ". Tente novamente ou confira a configuração.");
    }

    private String upstreamErrorDetails(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            JsonNode error = jsonMapper.readTree(responseBody).path("error");
            String code = safeErrorField(error.path("code").asString(""));
            String parameter = safeErrorField(error.path("param").asString(""));
            if (!code.isBlank() && !parameter.isBlank()) return "; código " + code + ", campo " + parameter;
            if (!code.isBlank()) return "; código " + code;
            if (!parameter.isBlank()) return "; campo " + parameter;
        } catch (JacksonException ignored) {
            // O conteúdo bruto do provedor não é encaminhado ao usuário nem aos logs.
        }
        return "";
    }

    private String safeErrorField(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,80}")) return "";
        return apiKey != null && !apiKey.isBlank() ? value.replace(apiKey, "") : value;
    }

    private String systemPromptWithFleet() {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT).append("\nFrota cadastrada:\n");
        List<Car> fleet = cars.findAllByAvailableTrueOrderByDailyPriceAsc();
        for (Car car : fleet) {
            prompt.append("- ").append(car.make).append(' ').append(car.model)
                    .append(" ( ").append(car.year).append(", ").append(car.category).append(", ")
                    .append(car.transmission).append(", ").append(car.fuel).append(" ): R$ ")
                    .append(car.dailyPrice == null ? "preço sob consulta" : car.dailyPrice.toPlainString())
                    .append(" por dia.\n");
        }
        return prompt.toString();
    }

    private String extractReply(JsonNode response) {
        if (response == null) throw ApiException.badGateway("O serviço de conversa retornou uma resposta vazia.");
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw ApiException.badGateway("O serviço de conversa retornou um formato inesperado.");
        }
        String content = choices.path(0).path("message").path("content").asString("");
        if (content.isBlank()) {
            throw ApiException.badGateway("O serviço de conversa não retornou texto.");
        }
        return content.trim();
    }
}
