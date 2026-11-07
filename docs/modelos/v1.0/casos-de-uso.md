# Diagrama de casos de uso — v1.0

```mermaid
flowchart LR
    Visitante([Visitante])
    Cliente([Cliente autenticado])
    Empresa([Agente de empresa])
    Banco([Agente de banco])
    Cadastro((Cadastrar-se))
    Login((Entrar no sistema))
    Perfil((Manter cadastro e rendimentos))
    Criar((Criar pedido de aluguel))
    Consultar((Consultar pedido e situação))
    Alterar((Modificar pedido))
    Cancelar((Cancelar pedido))
    Editar((Modificar dados do pedido))
    Analisar((Avaliar financeiramente))
    Parecer((Registrar parecer))
    Executar((Executar contrato))
    Automovel((Registrar automóvel e proprietário))
    Credito((Associar crédito bancário))

    Visitante --> Cadastro
    Visitante --> Login
    Cliente --> Perfil
    Cliente --> Criar
    Cliente --> Consultar
    Cliente --> Alterar
    Cliente --> Cancelar
    Empresa --> Consultar
    Empresa --> Editar
    Empresa --> Analisar
    Empresa --> Parecer
    Empresa --> Executar
    Empresa --> Automovel
    Banco --> Consultar
    Banco --> Editar
    Banco --> Analisar
    Banco --> Parecer
    Banco --> Executar
    Banco --> Automovel
    Banco --> Credito
    Executar --> Automovel
    Credito -. opcional .-> Executar
```

**Atores:** visitante, cliente autenticado, agente de empresa e agente de banco. As ações de negócio exigem autenticação; o visitante só pode cadastrar-se ou iniciar o login.
