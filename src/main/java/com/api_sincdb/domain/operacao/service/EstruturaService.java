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
import com.api_sincdb.domain.operacao.model.EstruturaTabela;
import com.api_sincdb.domain.operacao.model.ResultadoComparacao;
import com.api_sincdb.enums.TipoConexao;
import com.api_sincdb.util.UtilsSync;
import com.api_sincdb.websocket.LogPublisher;

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
    private CriacaoTabelaService criacaoTabelaService;

    @Autowired
    private LogPublisher logPublisher;

    // ================================================================
    // FUNCOES PRINCIPAIS
    // ================================================================

    public Map<String, Object> verificarEstrutura(String token, String database, String esquema, String nomeTabela) {

        Map<String, Object> response = new LinkedHashMap<>();
        List<EstruturaTabela> detalhes = new ArrayList<>();

        try (Connection conexaoCloud = conexaoBanco.abrirConexao(database, TipoConexao.CLOUD, token);
                Connection conexaoLocal = conexaoBanco.abrirConexao(database, TipoConexao.LOCAL, token)) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelado");
            Set<String> tabelasLocal = obterTabelas(conexaoLocal, database, nomeTabela);
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelado");
            Set<String> tabelasCloud = obterTabelas(conexaoCloud, database, nomeTabela);
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelado");

            HashMap<String, List<String>> queries = processarTabelas(conexaoCloud, conexaoLocal, tabelasCloud,
                    tabelasLocal, detalhes, database, esquema, nomeTabela);

            if (queries != null) {
                cacheService.salvarCache(database + "_estrutura:", queries);
                response.put("tabelas_afetadas", detalhes);
            }

            response.put("sucesso", true);
        } catch (InterruptedException e) {
            utilsSync.tratarErroCancelamento(response, e);
            Thread.currentThread().interrupt();
        } catch (SQLException e) {
            utilsSync.tratarErroSincronizacao(response, e);
        } catch (Exception e) {
            utilsSync.tratarErroSincronizacao(response, e);
        }

        return response;
    }

    public Map<String, Object> sincronizarEstrutura(String token, String database) {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, String>> detalhes = new ArrayList<>();

        try (Connection conexaoLocal = conexaoBanco.abrirConexao(database, TipoConexao.LOCAL, token)) {
            @SuppressWarnings("unchecked")
            HashMap<String, List<String>> querys = cacheService.buscarCache(database + "_estrutura:", HashMap.class);

            if (querys == null) {
                response.put("sucesso", false);
                response.put("message", "Nenhuma verificação foi feita previamente.");
                logPublisher.enviarLog("Nenhuma verificação foi feita previamente.");
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

        } catch (SQLException e) {
            utilsSync.tratarErroSincronizacao(response, e);
        } catch (Exception e) {
            utilsSync.tratarErroSincronizacao(response, e);
        }
        return response;
    }

    public HashMap<String, List<String>> processarTabelas(
            Connection conexaoCloud,
            Connection conexaoLocal,
            Set<String> tabelasCloud,
            Set<String> tabelasLocal,
            List<EstruturaTabela> detalhes,
            String database,
            String esquema,
            String nomeTabela)
            throws SQLException, InterruptedException {
        List<String> criacaoSchema = Collections.synchronizedList(new ArrayList<>());
        List<String> sequencias = Collections.synchronizedList(new ArrayList<>());
        List<String> criacoesTabela = Collections.synchronizedList(new ArrayList<>());
        List<String> chavesEstrangeiras = Collections.synchronizedList(new ArrayList<>());
        List<String> alteracoes = Collections.synchronizedList(new ArrayList<>());
        List<String> funcoes = Collections.synchronizedList(new ArrayList<>());
        List<String> extensoes = Collections.synchronizedList(new ArrayList<>());
        List<String> views = Collections.synchronizedList(new ArrayList<>());
        List<String> dropViewsDependentes = Collections.synchronizedList(new ArrayList<>());
        List<String> createViewsDependentes = Collections.synchronizedList(new ArrayList<>());

        processoService.iniciarProcesso(database);

        int totalTabelas = tabelasCloud.size();
        AtomicInteger tabelasProcessadas = new AtomicInteger(0);
        processoService.enviarProgresso("Iniciando", 0, "Iniciando processamento de " + totalTabelas + " tabelas",
                null);

        logPublisher.enviarLog("Iniciando processamento de " + totalTabelas + " tabelas");

        Set<String> verificarSchemasCriados = new HashSet<>();

        // CRIAR SEQUENCIA
        String sequenciaQuery = databaseService.criarSequenciaQuery(conexaoCloud, conexaoLocal, esquema);
        if (sequenciaQuery != null)
            sequencias.add(sequenciaQuery);

        // CRIAR FUNCOES
        List<String> funcao = databaseService.criarFuncoesQuery(conexaoCloud, conexaoLocal);
        if (funcao.size() > 0) {
            EstruturaTabela infoEstruturaFuncao = new EstruturaTabela();
            funcoes.addAll(funcao);
            infoEstruturaFuncao.setTabela("Todas");
            infoEstruturaFuncao.setAcao("Funçao");
            detalhes.add(infoEstruturaFuncao);

        }

        // CRIAR EXTENSOES
        List<String> extencao = databaseService.gerarScriptsExtensoes(conexaoCloud, conexaoLocal);
        if (extencao.size() > 0) {
            EstruturaTabela infoEstruturaExtencao = new EstruturaTabela();
            extensoes.addAll(extencao);
            infoEstruturaExtencao.setTabela("Todas");
            infoEstruturaExtencao.setAcao("Extencao");
            detalhes.add(infoEstruturaExtencao);
        }

        // CRIAR VIEWS
        List<String> scriptsViews = databaseService.gerarScriptsViews(conexaoCloud, conexaoLocal, esquema);
        if (!scriptsViews.isEmpty()) {
            views.addAll(scriptsViews);

            EstruturaTabela infoEstruturaViews = new EstruturaTabela();
            infoEstruturaViews.setTabela("Todas");
            infoEstruturaViews.setAcao("Views");
            detalhes.add(infoEstruturaViews);
        }

        for (String itemTabela : tabelasCloud) {

            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelado");

            int progresso = (int) ((tabelasProcessadas.incrementAndGet() / (double) totalTabelas) * 100);
            processoService.enviarProgresso("Processando", progresso, "Processando tabela: " + itemTabela, itemTabela);

            EstruturaTabela infoEstrutura = new EstruturaTabela();

            if (!tabelasLocal.contains(itemTabela)) {
                logPublisher.enviarLog("Criando estrutura da tabela: " + itemTabela);

                String schema = utilsSync.extrairSchema(itemTabela);
                if (schema != null && !verificarSchemasCriados.contains(schema)) {
                    String querySchema = databaseService.gerarQueryCriacaoSchemas(conexaoLocal, schema);
                    if (querySchema != null && !querySchema.isBlank()) {
                        criacaoSchema.add(querySchema);
                        verificarSchemasCriados.add(schema);
                    }
                }

                String queryTabela = criacaoTabelaService.criarEstuturaTabela(conexaoCloud, itemTabela);
                if (queryTabela != null && !queryTabela.isBlank()) {
                    criacoesTabela.add(queryTabela);
                    infoEstrutura.setTabela(itemTabela);
                    infoEstrutura.setAcao("Criação");
                    detalhes.add(infoEstrutura);
                }

                String fkQuery = databaseService.obterChaveEstrangeira(conexaoCloud, itemTabela);
                if (fkQuery != null)
                    chavesEstrangeiras.add(fkQuery);
            } else {
                logPublisher.enviarLog("Verificando alteração na tabela: " + itemTabela);

                ResultadoComparacao resultado = atualizarEstruturaService.compararEstruturaTabela(conexaoCloud,
                        conexaoLocal, itemTabela);

                if (resultado.hasChanges()) {

                    atualizarEstruturaService.gerarScriptsViewsDependentes(
                            conexaoCloud,
                            conexaoLocal,
                            itemTabela,
                            resultado,
                            dropViewsDependentes,
                            createViewsDependentes);

                    alteracoes.addAll(resultado.getAlteracoes());

                    infoEstrutura.setTabela(itemTabela);
                    infoEstrutura.setAcao("Atualização");
                    detalhes.add(infoEstrutura);
                }

            }

        }

        processoService.enviarProgresso("Concluido", 100, "Processamento concluído com sucesso", null);
        logPublisher.enviarLog("Verificação concluída com sucesso");

        HashMap<String, List<String>> queries = new LinkedHashMap<>();
        queries.put("Schemas", criacaoSchema);
        queries.put("Sequências", sequencias);
        queries.put("Criação de Tabelas", criacoesTabela);
        queries.put("Chaves Estrangeiras", chavesEstrangeiras);
        queries.put("DropViewsDependentes", dropViewsDependentes);
        queries.put("Alterações", alteracoes);
        queries.put("CreateViewsDependentes", createViewsDependentes);
        queries.put("Views", views);
        queries.put("Extenção", extencao);
        queries.put("Função", funcoes);

        return queries;
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
