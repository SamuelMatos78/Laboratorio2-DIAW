# Diagrama de pacotes — visão lógica v1.0

```mermaid
flowchart TB
    subgraph Web[Subsistema de páginas web — React + Vite]
        Pages[Páginas e telas]
        Components[Componentes reutilizáveis]
        ApiClient[Cliente HTTP / serviços]
        Pages --> Components
        Pages --> ApiClient
    end

    subgraph API[Subsistema de gestão — Spring Boot MVC]
        Controller[api / controllers]
        Service[service]
        Domain[domain]
        Repository[repository]
        Security[security e config]
        Controller --> Service
        Service --> Domain
        Service --> Repository
        Security --> Controller
    end

    PostgreSQL[(Supabase PostgreSQL)]
    ApiClient -->|HTTP JSON + Bearer token| Controller
    Repository --> PostgreSQL
```

Controllers validam a entrada HTTP; services concentram as regras; entidades representam o domínio; repositories persistem no PostgreSQL do Supabase. O front-end não acessa o banco diretamente.
