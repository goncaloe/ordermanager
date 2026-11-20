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

## Testes

Os testes de integração correm numa bd real PostgreSQL (via Testcontainers), com as migrações Flyway aplicadas, por isso é necessário ter o Docker a correr.

```bash
mvn test
```

A suite cobre o cumprimento de encomendas (FIFO, alocações parciais, stock negativo), o `update`, a paginação, o mapeamento dos DTOs fora da transacção e cenários concorrentes (`OrderConcurrencyIntegrationTest`). 
Nos testes concorrentes as regras de integridade (nenhum movimento/encomenda alocado acima da sua quantidade, stock disponível nunca negativo) são verificados por SQL diretamente na BD, não pelo estado em memória.

## Swagger e documentação

Com a aplicação a correr, abrir:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Aí é possível ver todos os endpoints e executá-los diretamente no browser.

## Notas de implementação

- Usei o Spring Initializr https://start.spring.io/ para gerar o pom.xml com as dependências do maven.
- Modelagem da base de dados postgreSQL:
  - Chaves das tabelas BIGINT. Com `GENERATED ALWAYS AS IDENTITY` para auto incremento nas chaves primárias.
  - Campos `TIMESTAMP` para datas, sem o timezone da bd.
  - Adicionados alguns constraints e checks de campos int.
  - Adicionados também indices para melhor performance nas consultas.
  - O enunciado indica que se deve registar os movimentos ao completar encomendas. Com apenas uma tabela de `orders` os movimentos ficariam limitados à quantidade da encomenda. Por isso considero que o melhor é registar os movimentos das completar orders numa tabela separada `order_movements`.
- Hibernate com ddl-auto em movo validate para não fazer alterações na bd. Alterações na estrutura feitas apenas pelo flyway.
- CRUD dos utilizadores. Mensagens de excepções e validação a inglês. Comentários em PT.
- Update de um utilizador deve verificar se já existe algum registo com o email do pedido PUT (excepto o próprio registo). A verificação desta restrição é feita numa única query com os 2 campos `email` e `id`, mas poderia ser feita em 2 passos: 1º find do user pelo `email` (retorna no máximo 1 registo) e depois 2º verificar via stream.filter() que resultado não é o próprio registo (do {id} no url).
- Handler de excepções globais com formatação unificada dos erros com `ProblemDetail` (RFC 9457). Baseado na implementação que fiz ha uns meses atras num projeto github: https://github.com/goncaloe/timegrid/blob/main/src/main/java/com/timegrid/controller/ApiExceptionHandler.java
- Create e update de um item deve verificar que não existe outro registo com o `name` igual.
- CRUD dos StockMovement. A quantidade alocada de cada movimento é calculada a partir das alocações (`order_movements`), não é guardada em coluna.
- StockMovement (tabela `stock_movements`) é imutável depois de criado. Não se apagam registos e não existe endpoint `DELETE` (devolve 405). Se o negócio precisa de corrigir, cria-se um novo registo de movimento com quantidade negativa.
- OrderMovement (tabela `order_movements`) tem o registo de movimentos de stock para uma encomenda. Cada linha de `order_movements` é uma alocação. O valor de `quantity` pode alterar até corresponder para completar a alocação da order e StockMovement correspondente.
- Criado service `StockAllocator` partilhado que não é de api, mas para fazer a gestão de entradas alocadas de stock e dos movimentos na criação de encomendas. Desta forma a lógica não fica dumplicada nos 2 services: orderService stockMovementService.
- Quando entra stock de um item, verifica cada order incompleta para alocar
- Adicionado logs nos services OrderService e StockMovementService
- Update de uma encomenda (`PUT /api/orders/{id}`) aplica o item, o utilizador e a quantidade e recalcula as alocações:
  - *reduzir* a quantidade liberta o excesso (as alocações mais recentes são reduzidas/removidas) e o stock libertado é atribuído às encomendas pendentes;
  - *aumentar* a quantidade volta a tentar alocar o que falta;
  - *mudar de item* liberta as alocações do item antigo e aloca no novo.
- Listagem de encomendas com paginação em duas fases: 1ª query devolve só os ids da página, 2ª carrega as entidades com `JOIN FETCH`. Combinar `Pageable` com `JOIN FETCH` de uma coleção faz o Hibernate paginar em memória e o `LIMIT` passa a contar linhas do join (a mesma encomenda podia aparecer repetida e outra nunca aparecer)
- Aplicado lock pessimista por item, ordem de locks fixa e testes concorrentes. Resolve concorrencia na atribuição de stock
- Endpoint `StockMovementService.delete()` removido. Correção de stock faz-se com um movimento negativo. Mantem rastreabilidade dos registos.


## Concorrência

- O recurso partilhado é o *stock do item*, não a encomenda. Por isso todas as operações que decidem alocações começam por bloquear a linha do item (`SELECT ... FOR UPDATE`, ver `ItemRepository.findByIdForUpdate` e `ItemService.lock`): criar/alterar/apagar encomendas, registar movimentos de stock e apagar itens. Duas transacções do mesmo item ficam serializadas, pelo que já não podem calcular a mesma disponibilidade e alocar as mesmas unidades.
- Ordem global de locks: *items (id ascendente) -> orders*. É sempre a mesma em todos os caminhos, para não haver ciclos (deadlocks) entre a criação de encomendas e a chegada de stock. O `update` bloqueia os dois itens por id ascendente quando a encomenda muda de item.
- O `SELECT ... FOR UPDATE` das encomendas bloqueia apenas a linha de `orders` (sem `JOIN FETCH`), para reduzir o âmbito do lock.
- Os locks têm timeout (`jakarta.persistence.lock.timeout` = 5s). Um timeout/deadlock devolve *409 Conflict* com um `ProblemDetail`, porque o pedido não chegou a correr e pode ser repetido.
- O estado de alocação é derivado de `order_movements` (`SUM(quantity)`). A alternativa seria usar `FUNCTION`/`TRIGGER` no postgreSQL mas parte da lógica ficaria na bd. Os `SUM` são apoiados por índices com `INCLUDE (quantity)`.