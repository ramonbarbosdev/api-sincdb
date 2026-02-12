package com.api_sincdb.domain.operacao.service;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.swing.Spring;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.info.service.SincronizacaoSchemaService;
import com.api_sincdb.domain.operacao.model.EstruturaTabela;
import com.api_sincdb.domain.operacao.model.ResultadoComparacao;
import com.api_sincdb.domain.operacao.model.TerminalLog;
import com.api_sincdb.enums.TipoConexao;
import com.api_sincdb.enums.TipoOperacao;
import com.api_sincdb.helper.EstruturaDadosUtils;
import com.api_sincdb.helper.JwtHelper;
import com.api_sincdb.util.MontarEstruturaResponseUtils;
import com.api_sincdb.util.ThreadUtils;
import com.api_sincdb.util.UtilsSync;
import com.api_sincdb.websocket.LogPublisher;

import io.jsonwebtoken.JwtHandler;
import org.apache.commons.lang3.tuple.Pair;

@Service
public class EstruturaService {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private AtualizarEstruturaService atualizarEstruturaService;

    @Autowired
    private ProcessoService processoService;

    @Autowired
    private UtilsSync utilsSync;

    @Autowired
    private OperacaoBancoService operacaoBancoService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ConexaoBanco conexaoBanco;

    @Autowired
    private CriacaoTabelaEstruturaService criacaoTabelaService;

    @Autowired
    private LogPublisher logPublisher;

    @Autowired
    private SincronizacaoSchemaService sincronizacaoSchemaService;

    @Autowired
    private EstruturaDadosUtils estruturaDadosUtils;

    @Autowired
    private JwtHelper jwtHelper;

    // ================================================================
    // FUNCOES PRINCIPAIS
    // ================================================================

    public Map<String, Object> verificarEstrutura(String token, String database, String esquema, String nomeTabela)
            throws Exception {

        Map<String, Object> response = new LinkedHashMap<>();
        List<EstruturaTabela> detalhes = new ArrayList<>();

        String usuario = jwtHelper.extrairUsuario(token);

        Pair<Connection, Connection> conexoes = estruturaDadosUtils.abrirConexoes(database, token);
        Connection cloud = conexoes.getLeft();
        Connection local = conexoes.getRight();

        try {

            sincronizacaoSchemaService.iniciar(database, esquema, usuario, TipoOperacao.ESTRUTURA);

            ThreadUtils.verificarCancelamento();
            Set<String> tabelasLocal = obterTabelas(local, database, nomeTabela);

            ThreadUtils.verificarCancelamento();
            Set<String> tabelasCloud = obterTabelas(cloud, database, nomeTabela);

            ThreadUtils.verificarCancelamento();

            HashMap<String, List<String>> queries = (HashMap<String, List<String>>) processarTabelas(
                    cloud,
                    local,
                    tabelasCloud,
                    tabelasLocal,
                    detalhes,
                    database,
                    esquema,
                    nomeTabela);

            if (queries != null) {

                cacheService.salvarCache(database + "_estrutura:", queries);
                Map<String, List<EstruturaTabela>> categorias = MontarEstruturaResponseUtils
                        .montarDetalhesPorCategoria(queries);

                response.putAll(categorias);
            }

            response.put("sucesso", true);

            sincronizacaoSchemaService.marcarComoDesatualizado(database, esquema, usuario,
                    "Scripts gerados para sincronização.", TipoOperacao.ESTRUTURA);

        } catch (InterruptedException e) {
            Thread.interrupted();
            estruturaDadosUtils.finalizarCancelado(database, esquema, usuario, response, e, TipoOperacao.ESTRUTURA);
        } catch (SQLException e) {

            estruturaDadosUtils.finalizarErro(database, esquema, usuario, response, e, TipoOperacao.ESTRUTURA);

        } catch (Exception e) {

            estruturaDadosUtils.finalizarErro(database, esquema, usuario, response, e, TipoOperacao.ESTRUTURA);

        } finally {

            if (cloud != null)
                cloud.close();
            if (local != null)
                local.close();
        }

        return response;
    }

    public Map<String, Object> sincronizarEstrutura(String token, String database, String esquema) {

        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, String>> detalhes = new ArrayList<>();

        String usuario = jwtHelper.extrairUsuario(token);

        try (Connection conexaoLocal = conexaoBanco.abrirConexao(database, TipoConexao.LOCAL, token)) {

            sincronizacaoSchemaService.iniciar(database, esquema, usuario, TipoOperacao.ESTRUTURA);

            @SuppressWarnings("unchecked")
            HashMap<String, List<String>> querys = cacheService.buscarCache(database + "_estrutura:", HashMap.class);

            if (querys == null) {
                response.put("sucesso", false);
                response.put("message", "Nenhuma verificação foi feita previamente.");
                logPublisher.enviarLog(TerminalLog.warn("Nenhuma verificação foi feita previamente."));

                sincronizacaoSchemaService.finalizarErro(database,
                        esquema, usuario, "Sincronização abortada: scripts não encontrados.", TipoOperacao.ESTRUTURA);

                return response;
            }

            operacaoBancoService.executarQueriesEmLotes(conexaoLocal, querys, detalhes);

            List<String> listaErro = new ArrayList<>();
            for (Map<String, String> erro : detalhes) {
                listaErro.add(erro.get("erro"));
            }

            response.put("sucesso", true);
            response.put("tabelas_afetadas", detalhes);
            response.put("errors", listaErro);

            sincronizacaoSchemaService.finalizarSucesso(database,
                    esquema, usuario,
                    "Sincronização concluída. Total de tabelas processadas: " + detalhes.size(),
                    TipoOperacao.ESTRUTURA);


        } catch (Exception e) {

            sincronizacaoSchemaService.finalizarErro(database,
                    esquema,
                    usuario,
                    "Erro ao sincronizar scripts: " + e.getMessage(), TipoOperacao.ESTRUTURA);

            utilsSync.tratarErroSincronizacao(response, e);
        }

        return response;
    }

    public Map<String, List<String>> processarTabelas(
            Connection conexaoCloud,
            Connection conexaoLocal,
            Set<String> tabelasCloud,
            Set<String> tabelasLocal,
            List<EstruturaTabela> detalhes,
            String database,
            String esquema,
            String nomeTabela)
            throws SQLException, InterruptedException {

        processoService.iniciarProcesso(database);

        logPublisher.enviarLog(
                TerminalLog.warn("Iniciando processamento"));

        Map<String, List<String>> resultado = new LinkedHashMap<>();

        Map<String, List<String>> infraBase = construirInfraestruturaBanco(
                conexaoCloud, conexaoLocal, database, esquema, detalhes);

        Map<String, List<String>> tabelas = processarTabelasIndividuais(
                conexaoCloud, conexaoLocal, tabelasCloud, tabelasLocal, detalhes, database);

        resultado.put("Schemas", tabelas.getOrDefault("Schemas", List.of()));
        // resultado.put("Sequências", infraBase.getOrDefault("Sequências", List.of()));
        resultado.put("Criação de Tabelas", tabelas.getOrDefault("Criação de Tabelas", List.of()));
        resultado.put("Chaves Estrangeiras", tabelas.getOrDefault("Chaves Estrangeiras", List.of()));
        resultado.put("DropViewsDependentes", tabelas.getOrDefault("DropViewsDependentes", List.of()));
        resultado.put("Alterações", tabelas.getOrDefault("Alterações", List.of()));
        resultado.put("CreateViewsDependentes", tabelas.getOrDefault("CreateViewsDependentes", List.of()));
        resultado.put("Views", infraBase.getOrDefault("Views", List.of()));
        resultado.put("Extensões", infraBase.getOrDefault("Extensões", List.of()));
        resultado.put("Funções", infraBase.getOrDefault("Funções", List.of()));

        processoService.enviarProgresso("Concluído", 100, "Verificação concluída.", null);
        logPublisher.enviarLog(
                TerminalLog.done("Verificação concluída."));

        return resultado;
    }

    public Map<String, List<String>> construirInfraestruturaBanco(
            Connection conexaoCloud,
            Connection conexaoLocal,
            String database,
            String esquema,
            List<EstruturaTabela> detalhes) throws SQLException {

        logPublisher.enviarLog(
                TerminalLog.info("Construindo infraestrutura do banco"));

        Map<String, List<String>> infra = new LinkedHashMap<>();

        List<String> schemas = new ArrayList<>();
        List<String> sequencias = new ArrayList<>();
        List<String> funcoes = new ArrayList<>();
        List<String> extensoes = new ArrayList<>();
        List<String> views = new ArrayList<>();

        // 1. Sequências
        String sequenciaQuery = databaseService.criarSequenciaQuery(conexaoCloud, conexaoLocal, esquema);
        if (sequenciaQuery != null)
            sequencias.add(sequenciaQuery);

        // 2. Funções
        List<String> f = databaseService.criarFuncoesQuery(conexaoCloud, conexaoLocal);
        if (!f.isEmpty()) {
            funcoes.addAll(f);
            detalhes.add(new EstruturaTabela("Todas", "Função"));
            logPublisher.enviarLog(
                    TerminalLog.ok("Funções"));
        }

        // 3. Extensões
        List<String> e = databaseService.gerarScriptsExtensoes(conexaoCloud, conexaoLocal);
        if (!e.isEmpty()) {
            extensoes.addAll(e);
            detalhes.add(new EstruturaTabela("Todas", "Extensão"));
            logPublisher.enviarLog(
                    TerminalLog.ok("Extensões"));
        }

        // 4. Views
        List<String> v = databaseService.gerarScriptsViews(conexaoCloud, conexaoLocal, esquema);
        if (!v.isEmpty()) {
            views.addAll(v);
            detalhes.add(new EstruturaTabela("Todas", "Views"));
            logPublisher.enviarLog(
                    TerminalLog.ok("Views"));
        }

        infra.put("Extensões", extensoes);
        infra.put("Funções", funcoes);
        infra.put("Sequências", sequencias);
        infra.put("Views", views);

        return infra;
    }

    public Map<String, List<String>> processarTabelasIndividuais(
            Connection conexaoCloud,
            Connection conexaoLocal,
            Set<String> tabelasCloud,
            Set<String> tabelasLocal,
            List<EstruturaTabela> detalhes,
            String database)
            throws SQLException, InterruptedException {

        Map<String, List<String>> tabelas = new LinkedHashMap<>();

        List<String> criacaoSchema = new ArrayList<>();
        List<String> criacoesTabela = new ArrayList<>();
        List<String> fks = new ArrayList<>();
        List<String> alteracoes = new ArrayList<>();
        List<String> dropViewsDependentes = new ArrayList<>();
        List<String> createViewsDependentes = new ArrayList<>();

        Set<String> schemasCriados = new HashSet<>();

        int totalTabelas = tabelasCloud.size();
        AtomicInteger processadas = new AtomicInteger();

        processoService.enviarProgresso("Iniciando", 0, "Processando " + totalTabelas + " tabelas", null);
        logPublisher.enviarLog(
                TerminalLog.warn("Iniciando a verificação de " + totalTabelas + " tabelas"));

        for (String tabela : tabelasCloud) {

            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelado");

            int progresso = (int) ((processadas.incrementAndGet() / (double) totalTabelas) * 100);
            processoService.enviarProgresso("Processando", progresso, "Processando tabela: " + tabela, tabela);
            logPublisher.enviarLog(
                    TerminalLog.tabela(tabela));

            String schema = utilsSync.extrairSchema(tabela);

            if (!tabelasLocal.contains(tabela)) {

                // Criar schema, se necessário
                if (schema != null && schemasCriados.add(schema)) {
                    String schemaQuery = databaseService.gerarQueryCriacaoSchemas(conexaoLocal, schema);
                    if (schemaQuery != null && !schemaQuery.isBlank())
                        criacaoSchema.add(schemaQuery);
                    logPublisher.enviarLog(
                            TerminalLog.ok("Schema"));
                }

                // Criar tabela
                String create = criacaoTabelaService.criarEstruturaTabela(conexaoCloud, tabela);
                if (create != null && !create.isBlank()) {
                    criacoesTabela.add(create);
                    detalhes.add(new EstruturaTabela(tabela, "Criação"));
                    logPublisher.enviarLog(
                            TerminalLog.ok("Criação"));
                }

                // FK
                String fk = databaseService.obterChaveEstrangeira(conexaoCloud, tabela);
                if (fk != null)
                    fks.add(fk);

            } else {
                ResultadoComparacao resultado = atualizarEstruturaService.compararEstruturaTabela(conexaoCloud,
                        conexaoLocal, tabela);

                if (resultado.hasChanges()) {

                    atualizarEstruturaService.gerarScriptsViewsDependentes(
                            conexaoCloud, conexaoLocal, tabela, resultado,
                            dropViewsDependentes, createViewsDependentes);

                    alteracoes.addAll(resultado.getAlteracoes());
                    detalhes.add(new EstruturaTabela(tabela, "Atualização"));
                    logPublisher.enviarLog(
                            TerminalLog.ok("Atualização de estrutura"));
                } else {
                    logPublisher.enviarLog(
                            TerminalLog.info("Nenhuma criação ou atualização encontrada."));
                }
            }
        }

        tabelas.put("Schemas", criacaoSchema);
        tabelas.put("Criação de Tabelas", criacoesTabela);
        tabelas.put("DropViewsDependentes", dropViewsDependentes);
        tabelas.put("Alterações", alteracoes);
        tabelas.put("Chaves Estrangeiras", fks);
        tabelas.put("CreateViewsDependentes", createViewsDependentes);

        return tabelas;
    }

    // ================================================================
    // UTILITÁRIOS
    // ================================================================

    public Set<String> obterTabelas(Connection conexao, String base, String nomeTabela)
            throws InterruptedException, ExecutionException, TimeoutException {
        Set<String> tabelas = databaseService.obterTabelaMetaData(base, conexao);
        if (nomeTabela != null && !nomeTabela.isBlank()) {
            return tabelas.stream()
                    .filter(t -> t.contains(nomeTabela))
                    .collect(Collectors.toSet());
        }

        return tabelas;
    }

}
