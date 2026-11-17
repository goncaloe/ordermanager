# Order Manager
Gestão de stock / encomendas

## Pré-requisitos

- Java 21
- Docker (para o PostgreSQL local)

## Comandos

### 1. Arrancar a base de dados

```bash
docker compose up -d
```

Nota: usar `docker compose down -v` antes de `compose up` para o reset da bd.

PostgreSQL fica em `localhost:5432` (BD `ordermanager`, user/pass `ordermanager`).
As migrações Flyway em `src/main/resources/db/migration` são aplicadas no arranque da aplicação.

### 2. Arrancar a aplicação

```bash
mvn spring-boot:run
```

A aplicação fica disponível na porta 8080 por padrão.


## Notas de implementação

- Usei o Spring Initializr https://start.spring.io/ para gerar o pom.xml com as dependências do maven.
