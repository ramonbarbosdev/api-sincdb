# Estrutura SaaS Central Logica

Este projeto adota a separacao entre dados centrais e contexto de organizacao sem criar,
por enquanto, um database proprio para cada organizacao.

## Decisao atual

- A organizacao e um contexto logico de acesso.
- A API continua usando a persistencia atual da aplicacao.
- Nao ha provisionamento automatico de database por organizacao.
- Nao ha roteamento de datasource por tenant neste momento.
- O frontend envia apenas o identificador do tenant/organizacao.
- A API valida usuario, vinculo e organizacao ativa no backend.

## Fonte central de verdade

As colecoes atuais funcionam como a area central da plataforma:

- `usuarios`: usuarios globais.
- `empresa`: organizacoes/tenants logicos.
- `usuario_empresa`: vinculos entre usuarios e organizacoes.
- `role`: papeis globais.
- `plano_assinatura`: planos.
- `parametro_master`: parametros globais.
- `conexao`: configuracoes de conexao, que devem evoluir para pertencer a organizacao.

## Fluxo de autenticacao

1. O frontend chama `POST /auth/obter-organizacao` com login e senha.
2. A API autentica as credenciais no diretorio central.
3. A API retorna as organizacoes ativas vinculadas ao usuario e um token temporario.
4. O frontend chama `POST /auth/selecionar-organizacao` informando `id_tenant` e enviando o token temporario.
5. A API valida se o tenant existe, esta ativo e pertence ao usuario.
6. A API emite o token final com:
   - `id_usuario`;
   - `id_empresa`;
   - `id_tenant`.

O endpoint antigo `POST /auth/login` permanece compativel e executa a mesma selecao de organizacao.

## Runtime de tenant

Durante uma requisicao autenticada, o filtro JWT preenche:

- `TenantContext`: mantem apenas `id_tenant`.
- `TenantRuntimeContext`: mantem `id_usuario`, `id_empresa`, `id_tenant` e `login`.

Esses contextos existem somente durante a requisicao e sao limpos ao final do filtro.

## O que ainda nao sera feito

Por enquanto, nao serao implementados:

- database por organizacao;
- criacao automatica de database;
- Flyway por tenant;
- `TenantAwareRoutingDataSource`;
- migrations para remover FKs locais de identidade;
- provisionamento de tenant.

## Conexoes por organizacao

`Conexao` pertence a organizacao ativa e nao mais apenas ao usuario que cadastrou.

Campos principais:

- `id_empresa`: organizacao central dona da conexao.
- `id_tenant`: identificador logico do tenant/organizacao.
- `idUsuario`: usuario que cadastrou ou atualizou a conexao.
- `nm_conexao`: nome exibido para identificar a conexao.
- `fl_padrao`: indica qual conexao ativa sera usada pela sincronizacao.
- `fl_ativo`: permite desativar uma conexao sem apagar o historico.

Regras:

- Uma organizacao pode possuir multiplas conexoes.
- A primeira conexao ativa criada para a organizacao vira padrao automaticamente.
- Ao marcar uma conexao como padrao, as demais conexoes ativas da organizacao deixam de ser padrao.
- A sincronizacao usa a conexao padrao da organizacao ativa no `TenantRuntimeContext`.
- Existe fallback temporario para conexao antiga por usuario quando nao houver contexto de organizacao.

Endpoints principais:

- `POST /conexao/`: cria uma nova conexao na organizacao ativa.
- `PUT /conexao/`: atualiza uma conexao existente da organizacao ativa.
- `GET /conexao/{login}`: lista conexoes da organizacao ativa; sem contexto, lista as conexoes do usuario.
- `GET /conexao/{login}/{id}`: busca uma conexao especifica.
- `PUT /conexao/{login}/{id}/padrao`: marca uma conexao como padrao.
- `DELETE /conexao/{login}/{id}`: desativa uma conexao.

## Proximo passo recomendado

Com a estrutura de organizacao e conexoes pronta, o proximo passo recomendado e ajustar as telas/front-end para:

- listar varias conexoes;
- exibir qual conexao e padrao;
- permitir criar, editar, remover e marcar padrao;
- garantir que as telas de sincronizacao avisem qual conexao padrao sera usada.
