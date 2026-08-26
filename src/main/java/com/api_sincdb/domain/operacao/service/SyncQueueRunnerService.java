package com.api_sincdb.domain.operacao.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.operacao.model.SyncQueueItem;
import com.api_sincdb.enums.SyncQueueItemStatus;
import com.api_sincdb.enums.TipoOperacao;
import com.api_sincdb.util.SyncCacheKeys;
import com.api_sincdb.util.SyncExecutionGuard;

@Service
public class SyncQueueRunnerService {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private EstruturaService estruturaService;

    @Autowired
    private DadosService dadosService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private SyncExecutionGuard syncExecutionGuard;

    public void processItem(String token, SyncQueueItem item) {
        syncExecutionGuard.run(() -> executeItem(token, item));
    }

    private void executeItem(String token, SyncQueueItem item) {
        String base = item.getBaseNome();
        String esquema = item.getSchemaNome();
        TipoOperacao operacao = item.getOperacao();

        databaseService.garantirEsquemaLocal(base, esquema, token);

        List<SyncQueueScope> scopes = expandScopes(item);
        for (SyncQueueScope scope : scopes) {
            try {
                if (operacao == TipoOperacao.ESTRUTURA) {
                    runEstrutura(token, scope);
                } else {
                    runDados(token, scope);
                }
            } catch (Exception e) {
                throw new RuntimeException(
                        e.getMessage() != null ? e.getMessage() : "Falha ao processar escopo da fila",
                        e);
            }
        }
    }

    private void runEstrutura(String token, SyncQueueScope scope) throws Exception {
        String tabelaParam = resolveTabelaParam(scope.esquema, scope.tabela);
        Map<String, Object> verifyResult = estruturaService.verificarEstrutura(
                token,
                scope.base,
                scope.esquema,
                tabelaParam);

        if (!Boolean.TRUE.equals(verifyResult.get("sucesso"))) {
            throw new RuntimeException(extractErrorMessage(verifyResult, "Falha na verificação de estrutura"));
        }

        if (!estruturaTemPendencias(scope.base, scope.esquema, tabelaParam)) {
            return;
        }

        Map<String, Object> syncResult = estruturaService.sincronizarEstrutura(
                token,
                scope.base,
                scope.esquema,
                tabelaParam);

        validateSyncResult(syncResult, "Falha na sincronização de estrutura");
    }

    private void runDados(String token, SyncQueueScope scope) throws Exception {
        String tabelaParam = resolveTabelaParam(scope.esquema, scope.tabela);
        Map<String, Object> verifyResult = dadosService.verificarDados(
                token,
                scope.base,
                scope.esquema,
                tabelaParam);

        if (!Boolean.TRUE.equals(verifyResult.get("sucesso"))) {
            throw new RuntimeException(extractErrorMessage(verifyResult, "Falha na verificação de dados"));
        }

        if (!dadosTemPendencias(verifyResult)) {
            return;
        }

        Map<String, Object> syncResult = dadosService.sincronizarDados(
                token,
                scope.base,
                scope.esquema,
                tabelaParam,
                false);

        validateSyncResult(syncResult, "Falha na sincronização de dados");
    }

    private boolean estruturaTemPendencias(String base, String esquema, String tabelaParam) {
        @SuppressWarnings("unchecked")
        HashMap<String, List<String>> cache = cacheService.buscarCache(
                SyncCacheKeys.estrutura(base, esquema, tabelaParam),
                HashMap.class);
        return cache != null && !cache.isEmpty();
    }

    private boolean dadosTemPendencias(Map<String, Object> verifyResult) {
        Object afetadas = verifyResult.get("tabelas_afetadas");
        if (afetadas instanceof List<?> list) {
            return !list.isEmpty();
        }
        return false;
    }

    private void validateSyncResult(Map<String, Object> syncResult, String fallbackMessage) {
        if (!Boolean.TRUE.equals(syncResult.get("sucesso"))) {
            throw new RuntimeException(extractErrorMessage(syncResult, fallbackMessage));
        }

        Object errors = syncResult.get("errors");
        if (errors instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first != null && !String.valueOf(first).isBlank()) {
                throw new RuntimeException(String.valueOf(first));
            }
        }
    }

    private String extractErrorMessage(Map<String, Object> result, String fallback) {
        Object message = result.get("message");
        if (message != null && !String.valueOf(message).isBlank()) {
            return String.valueOf(message);
        }
        Object errors = result.get("errors");
        if (errors instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return String.valueOf(list.get(0));
        }
        return fallback;
    }

    private String resolveTabelaParam(String esquema, String tabela) {
        if (tabela != null && !tabela.isBlank() && !tabela.equals(esquema)) {
            return tabela;
        }
        return esquema;
    }

    private List<SyncQueueScope> expandScopes(SyncQueueItem item) {
        List<SyncQueueScope> scopes = new ArrayList<>();
        String base = item.getBaseNome();
        String esquema = item.getSchemaNome();

        if (item.getTabelas() != null && !item.getTabelas().isEmpty()) {
            for (String tabela : item.getTabelas()) {
                scopes.add(new SyncQueueScope(base, esquema, tabela));
            }
            return scopes;
        }

        if (item.getTabela() != null && !item.getTabela().isBlank()) {
            scopes.add(new SyncQueueScope(base, esquema, item.getTabela()));
            return scopes;
        }

        scopes.add(new SyncQueueScope(base, esquema, null));
        return scopes;
    }

    private record SyncQueueScope(String base, String esquema, String tabela) {
    }
}
