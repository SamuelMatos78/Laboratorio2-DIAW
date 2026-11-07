# Modelos do sistema

Os diagramas e histórias são mantidos em versões para permitir a comparação entre requisitos, modelo e código. A versão `v1.0` registra a modelagem inicial usada para construir o protótipo.

## Versões

| Versão | Conteúdo | Situação |
|---|---|---|
| [v1.0 — modelagem inicial](v1.0/) | Casos de uso, histórias do usuário, classes de domínio e pacotes lógicos | Base do protótipo |

## Alinhamento com a implementação atual

| Elemento do modelo | Implementação |
|---|---|
| Cliente e dados de identificação | `ClientProfile`, associado a `UserAccount` |
| Até três vínculos e rendimentos | `Employment`; limite validado no cadastro e na atualização |
| Empresa ou banco agente | `AgentProfile` com tipo `COMPANY` ou `BANK` |
| Automóvel | `Car`, associado ao pedido e ao contrato |
| Pedido e avaliação | `RentalRequest` e `RequestStatus` |
| Contrato e propriedade | `RentalContract` com tipo e nome do proprietário |
| Crédito bancário opcional | `CreditContract`, associado ao contrato e a um banco agente |
| Páginas dinâmicas | `frontend/src/App.jsx` (React) |
| API MVC | Controllers, services, repositories e entidades em `backend/src/main/java/br/edu/lab2/carros/` |

## Decisões registradas

- O status após parecer positivo é `AGUARDANDO_EXECUCAO`; a execução cria o contrato e altera o pedido para `CONTRATO_EXECUTADO`.
- Pedidos podem ser editados pelo cliente enquanto aguardam análise. Agentes podem ajustar os dados durante a análise.
- O protótipo usa tokens aleatórios Bearer mantidos em memória no processo do servidor. Ao reiniciar a API, os usuários precisam entrar novamente.
- Cadastro de agente requer `AGENT_INVITE_CODE`, configurado somente no back-end.

Atualizar os diagramas e esta tabela junto com alterações de requisitos ou de implementação.
