# Visao Geral do Sistema

## O que e este sistema

O `api_sincdb` e uma API Java com Spring Boot para sincronizacao de bancos de dados PostgreSQL entre dois ambientes: um banco na nuvem e um banco local.

O objetivo principal e comparar a estrutura e os dados desses dois bancos, identificar diferencas e gerar os comandos SQL necessarios para que o banco local fique alinhado ao banco da nuvem. O sistema tambem oferece autenticacao, cadastro de usuarios, empresas, configuracao de conexoes, acompanhamento de progresso e logs em tempo real.

## Problema que ele resolve

Em cenarios onde uma aplicacao precisa manter uma base local parecida com uma base principal na nuvem, podem surgir diferencas em tabelas, colunas, schemas, enums, views, funcoes, extensoes, sequencias e registros.

Este sistema automatiza esse processo em duas etapas:

1. Verificacao: compara cloud e local, gera scripts e salva os scripts em cache.
2. Sincronizacao: executa no banco local os scripts gerados na etapa anterior.

Com isso, o usuario consegue primeiro visualizar o que precisa ser alterado e depois aplicar a sincronizacao.

## Principais funcionalidades

- Autenticacao de usuarios com JWT.
- Cadastro e login de usuarios.
- Cadastro de empresas e vinculo com usuarios.
- Cadastro das conexoes de banco local e banco cloud por usuario.
- Upload de certificado criptografado para preencher dados da conexao cloud.
- Listagem de bases, schemas e tabelas do ambiente cloud.
- Verificacao da existencia de schemas no ambiente local.
- Comparacao da estrutura entre cloud e local.
- Geracao de SQL para criar ou atualizar estrutura do banco local.
- Comparacao de dados entre cloud e local.
- Geracao de SQL para carga inicial ou atualizacao de registros.
- Execucao dos scripts em lotes e transacoes.
- Controle de progresso e cancelamento de processos.
- Logs em tempo real via WebSocket/STOMP.
- Registro de historico de sincronizacao por usuario, base, schema e tipo de operacao.
- Parametros globais para controlar comportamento da sincronizacao.

## Arquitetura geral

O projeto segue uma arquitetura Spring Boot tradicional, separando responsabilidades em controllers, services, repositories, models e DTOs.

- `controller`: expoe os endpoints HTTP.
- `domain`: concentra modelos, DTOs, services e repositories por dominio.
- `config`: configuracoes de banco, Swagger, CORS, ambiente e tratamento global.
- `security`: configuracao de seguranca, JWT e filtros de autenticacao.
- `websocket`: configuracao de WebSocket e publicacao de logs.
- `util` e `helper`: funcoes auxiliares usadas pelos fluxos de sincronizacao.

O sistema usa PostgreSQL para conexao com os bancos sincronizados e MongoDB para persistir algumas entidades administrativas, como empresas, usuarios e parametros, conforme os repositories Mongo encontrados no projeto.

## Fluxo de uso esperado

1. O usuario faz cadastro ou login.
2. O sistema emite/usa um token JWT.
3. O usuario informa ou carrega as credenciais de conexao cloud e local.
4. O usuario consulta bases, schemas e tabelas disponiveis.
5. O usuario executa uma verificacao de estrutura ou de dados.
6. O sistema abre conexoes com cloud e local, compara os bancos e gera scripts SQL.
7. Os scripts sao armazenados em cache.
8. O usuario executa a sincronizacao.
9. O sistema aplica os scripts no banco local, registra progresso e envia logs em tempo real.
10. O historico da operacao fica disponivel na area de informacoes.

## Sincronizacao de estrutura

A sincronizacao de estrutura e responsavel por fazer o banco local acompanhar o desenho do banco cloud.

Durante a verificacao, o sistema compara:

- schemas;
- tabelas;
- colunas;
- tipos;
- enums;
- views;
- funcoes;
- extensoes;
- chaves estrangeiras;
- alteracoes necessarias em tabelas existentes;
- criacao de tabelas que existem na cloud e nao existem no local.

O fluxo principal fica em `EstruturaService`.

Na etapa de verificacao, o sistema:

1. Abre uma conexao com o banco cloud.
2. Abre uma conexao com o banco local.
3. Lista as tabelas existentes nos dois ambientes.
4. Identifica tabelas ausentes no local.
5. Gera scripts de criacao de schemas e tabelas.
6. Compara tabelas existentes e gera scripts de alteracao.
7. Gera scripts auxiliares para enums, views, funcoes e extensoes.
8. Salva os scripts no cache com a chave da base.

Na etapa de sincronizacao, o sistema busca os scripts previamente gerados no cache e executa os comandos no banco local em grupos.

## Sincronizacao de dados

A sincronizacao de dados e responsavel por copiar ou atualizar registros do banco cloud para o banco local.

O fluxo principal fica em `DadosService`.

Durante a verificacao de dados, o sistema:

1. Abre conexoes com cloud e local.
2. Calcula a ordem de carga das tabelas considerando dependencias e chaves estrangeiras.
3. Para tabelas novas ou vazias, gera comandos de carga inicial.
4. Para tabelas ja existentes, compara registros usando a chave primaria.
5. Gera comandos de insercao ou atualizacao.
6. Gera comandos para atualizar sequencias.
7. Salva os scripts no cache.

Durante a sincronizacao, o sistema:

1. Abre conexao com o banco local.
2. Desativa temporariamente constraints usando `session_replication_role = replica`.
3. Executa os scripts em lotes.
4. Reativa as constraints com `session_replication_role = origin`.
5. Registra sucesso ou erro da operacao.

## Controle de processos e logs

Operacoes de verificacao e sincronizacao podem ser longas. Por isso, o sistema possui:

- `ProcessoManager`, usado para iniciar, acompanhar e cancelar processos.
- `ProcessoService`, usado para enviar progresso.
- `LogPublisher`, usado para publicar logs em tempo real.
- `WebSocketConfig`, que habilita STOMP em `/sincdb-socket`.

Os logs e progresso podem ser consumidos por um frontend conectado ao WebSocket.

## Endpoints principais

Considerando o `context-path` configurado como `/sincdb`, os endpoints ficam abaixo desse prefixo.

### Autenticacao

- `POST /sincdb/auth/login`: autentica usuario.
- `POST /sincdb/auth/register`: cadastra usuario.
- `POST /sincdb/auth/obter-organizacao`: retorna empresa/organizacao vinculada ao login.
- `POST /sincdb/auth/logout`: encerra sessao.
- `GET /sincdb/auth/me`: retorna dados do usuario autenticado.

### Conexoes

- `POST /sincdb/conexao/`: cadastra dados de conexao cloud/local.
- `PUT /sincdb/conexao/`: atualiza dados de conexao cloud/local.
- `GET /sincdb/conexao/{login}`: recupera conexao do usuario.
- `POST /sincdb/conexao/certificado/upload/{login}`: processa certificado criptografado.
- `GET /sincdb/conexao/certificado`: le configuracao criptografada local.

### Consulta de bases e schemas

- `GET /sincdb/sincronizacao/bases/`: lista bases disponiveis na cloud.
- `GET /sincdb/sincronizacao/base/esquema/{base}`: lista schemas de uma base.
- `GET /sincdb/sincronizacao/base/tabela/{base}/{esquema}`: lista tabelas de uma base/schema.
- `GET /sincdb/sincronizacao/verificaesquema/{base}/{esquema}`: verifica schema no banco local.

### Estrutura

- `GET /sincdb/estrutura/verificar/{base}/{esquema}`: verifica diferencas de estrutura.
- `GET /sincdb/estrutura/verificar/{base}/{esquema}/{tabela}`: verifica estrutura de uma tabela especifica.
- `GET /sincdb/estrutura/{base}/{esquema}`: aplica a sincronizacao de estrutura.
- `GET /sincdb/estrutura/cancelar`: cancela processo em andamento.

### Dados

- `GET /sincdb/dados/verificar/{base}/{esquema}`: verifica diferencas de dados.
- `GET /sincdb/dados/verificar/{base}/{esquema}/{tabela}`: verifica dados de uma tabela especifica.
- `GET /sincdb/dados/{base}/{esquema}`: aplica a sincronizacao de dados.
- `GET /sincdb/dados/cancelar`: cancela processo em andamento.

### Informacoes e historico

- `GET /sincdb/info/atividade`: lista atividades de sincronizacao do usuario.
- `GET /sincdb/info/comparativo/bases`: compara bases entre ambientes.
- `GET /sincdb/info/comparativo/bases/{base}`: compara schemas de uma base.

### Empresas e parametros

- `POST /sincdb/empresa/cadastrar`: cadastra empresa.
- `GET /sincdb/empresa/sequencia`: gera sequencia de empresa.
- `POST /sincdb/parametromaster/cadastrar`: cadastra parametro global.
- `GET /sincdb/parametromaster/sequencia`: gera sequencia de parametro.
- `GET /sincdb/parametromaster/tipo-parametro`: lista tipos de parametro.

## Tecnologias utilizadas

- Java 17.
- Spring Boot.
- Spring Web.
- Spring Security.
- JWT com `jjwt`.
- Spring Data JPA.
- Spring Data MongoDB.
- PostgreSQL JDBC.
- HikariCP.
- jOOQ.
- Caffeine Cache.
- WebSocket/STOMP com SockJS.
- Springdoc OpenAPI/Swagger.
- Maven.

## Bancos e persistencia

O sistema trabalha com mais de um tipo de persistencia:

- PostgreSQL: usado como alvo/origem da sincronizacao e tambem como banco relacional suportado pela aplicacao.
- MongoDB: usado no profile ativo `mongo`, configurado em `application-mongo.properties`, para entidades administrativas do sistema.
- Cache em memoria: usado para guardar temporariamente os scripts gerados na etapa de verificacao antes da sincronizacao.

## Pontos importantes de comportamento

- A sincronizacao depende de uma verificacao previa, pois os scripts sao buscados do cache.
- A estrutura e os dados sao sincronizados em fluxos separados.
- O sentido principal observado no codigo e cloud para local.
- A execucao de SQL e feita em grupos, com commit por grupo.
- Erros criticos interrompem a execucao; erros nao criticos podem ser tolerados dependendo do parametro `PARAM_TOLERAR_ERROS_NAO_CRITICOS`.
- A sincronizacao de dados desativa constraints temporariamente para facilitar a carga.
- Processos longos podem ser cancelados.
- Logs e progresso sao enviados em tempo real via WebSocket.

## Como executar

Para gerar o pacote:

```bash
mvn clean package -DskipTests
```

Para executar o JAR:

```bash
java -jar nome-do-arquivo.jar
```

Com as configuracoes atuais, a aplicacao usa:

- porta `8081`;
- contexto `/sincdb`;
- Swagger em `/sincdb/swagger-ui.html`.

## Resumo em uma frase

Este sistema e uma API de sincronizacao que compara bancos PostgreSQL cloud e local, gera scripts de estrutura e dados, permite revisar/aplicar essas alteracoes e acompanha tudo com autenticacao, historico, progresso e logs em tempo real.
