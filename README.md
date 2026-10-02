# Fintech Backend

API de um app de finanças pessoais que lê as contas do próprio usuário via **Open Finance** (agregador [Pluggy](https://pluggy.ai)) e transforma transações em orçamentos, metas, insights e avisos.

Frontend: [fintech-frontend](https://github.com/ronaldozx/fintech-frontend)

## O que ela faz

- **Autenticação e perfil**: cadastro, login com JWT, edição de perfil e troca de senha.
- **Open Finance**: conexão de bancos pela Pluggy, sincronização de transações, contas, cartões e investimentos.
- **Transações**: listagem com filtros, edição, lançamentos manuais, categorização, conciliação de transferências entre contas próprias e exportação em CSV.
- **Orçamentos**: limite mensal por categoria (ou total) com acompanhamento do quanto já foi gasto.
- **Metas**: objetivos de economia com aportes e acompanhamento de prazo.
- **Insights**: taxa de poupança, projeção do mês, categorias que mais mudaram, cobranças recorrentes e gastos fora do padrão.
- **Agenda**: próximos vencimentos (faturas e cobranças fixas detectadas no histórico).
- **Investimentos**: carteira lida dos bancos, alocação por tipo, concentração e cobertura da reserva de emergência.
- **Notificações**: alertas de orçamento, metas e saúde da sincronização.
- **Assistente**: sugestões geradas por **regras** a partir dos dados do próprio usuário. Não usa IA externa e nunca recomenda um produto específico.
- **Privacidade**: exportação de todos os dados do usuário e exclusão completa da conta.

## Stack

- Java 21, Spring Boot 4
- Spring Security com autenticação stateless via JWT (filtro próprio)
- Spring Data JPA / Hibernate
- SQL Server
- MapStruct e Lombok
- Maven (wrapper incluso)

## Como rodar

Pré-requisitos: JDK 21+ e uma instância do SQL Server (a configuração de exemplo usa o SQL Server Express local). Crie o banco vazio:

```sql
CREATE DATABASE fintech;
```

Copie o arquivo de exemplo e preencha:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

| Chave | O que colocar |
| --- | --- |
| `spring.datasource.*` | URL, usuário e senha do seu SQL Server |
| `jwt.secret` | Uma string longa e aleatória (use pelo menos 32 caracteres) |
| `pluggy.client-id` / `pluggy.client-secret` | Credenciais da sua aplicação no [dashboard da Pluggy](https://dashboard.pluggy.ai) |

O `application.properties` está no `.gitignore`. Nunca versione credenciais.

Suba a API:

```bash
./mvnw spring-boot:run
```

Ela fica em `http://localhost:8080`. As tabelas são criadas na primeira execução (`ddl-auto=update`).

Sem as chaves da Pluggy a API sobe normalmente, mas a conexão com bancos não funciona. O resto (lançamentos manuais, orçamentos, metas, etc.) segue disponível.

## Testes

```bash
./mvnw test
```

## Estrutura

```
src/main/java/com/globo/fintech_backend
├── Auth            usuários, login, perfil
├── OpenFinance     integração com a Pluggy
├── Transactions    transações, categorias, conciliação
├── Budgets         orçamentos
├── Goals           metas e aportes
├── Insights        análises do mês
├── Agenda          próximos vencimentos
├── Investments     carteira e reserva de emergência
├── Notifications   alertas
├── Advisor         assistente baseado em regras
├── Privacy         exportação e exclusão de conta
├── security        configuração do Spring Security e filtro JWT
└── exception       tratamento centralizado de erros
```

Cada módulo segue o mesmo desenho: `controller`, `service`, `repository`, `dto` e `entity`.

## Aviso

O projeto é educacional. As sugestões do assistente e as telas de investimento não são recomendação financeira nem de investimento.
