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
- Modelagem da base de dados postgreSQL:
  - Chaves das tabelas BIGINT. Com `GENERATED ALWAYS AS IDENTITY` para auto incremento nas chaves primárias.
  - Campos `TIMESTAMP` para datas, sem o timezone da bd.
  - Adicionados alguns constraints e checks de campos int.
  - O enunciado indica que se deve registar os movimentos ao completar encomendas. Com apenas uma tabela de `orders` os movimentos ficariam limitados à quantidade da encomenda. Por isso considero que o melhor é registar os movimentos das completar orders numa tabela separada `order_movements`.
- Hibernate com ddl-auto em movo validate para não fazer alterações na bd. Alterações na estrutura feitas apenas pelo flyway.
