package com.api_sincdb.domain.operacao.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.operacao.dto.SyncQueueEnqueueRequest;
import com.api_sincdb.domain.operacao.dto.SyncQueueStatusResponse;
import com.api_sincdb.domain.operacao.model.SyncQueueItem;
import com.api_sincdb.domain.operacao.repository.SyncQueueItemRepository;
import com.api_sincdb.enums.SyncQueueItemStatus;
import com.api_sincdb.enums.TipoOperacao;
import com.api_sincdb.helper.JwtHelper;

import jakarta.annotation.PreDestroy;

@Service
public class SyncQueueService {

  private static final List<SyncQueueItemStatus> ACTIVE_STATUSES = List.of(
      SyncQueueItemStatus.PENDING,
      SyncQueueItemStatus.RUNNING);

  @Autowired
  private SyncQueueItemRepository repository;

  @Autowired
  private SyncQueueRunnerService runner;

  @Autowired
  private JwtHelper jwtHelper;

  @Autowired
  private SyncQueueAuthContext syncQueueAuthContext;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Set<String> runningUsers = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<String, String> currentItemByUser = new ConcurrentHashMap<>();

  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }

  public List<SyncQueueItem> listForUser(String usuario) {
    return repository.findByUsuarioOrderByCreatedAtAsc(usuario);
  }

  public SyncQueueStatusResponse statusForUser(String usuario) {
    int pending = repository.findByUsuarioAndStatusInOrderByCreatedAtAsc(
        usuario,
        List.of(SyncQueueItemStatus.PENDING)).size();
  return new SyncQueueStatusResponse(
      runningUsers.contains(usuario),
      pending,
      currentItemByUser.get(usuario));
  }

  public SyncQueueItem enqueue(String usuario, SyncQueueEnqueueRequest request) {
    validateEnqueueRequest(request);

    if (hasActiveScope(usuario, request)) {
      throw new IllegalStateException("Este escopo já está na fila ou em execução.");
    }

    SyncQueueItem item = new SyncQueueItem();
    item.setUsuario(usuario);
    item.setOperacao(request.getOperacao());
    item.setBaseNome(request.getBase());
    item.setSchemaNome(request.getEsquema());
    item.setTabela(request.getTabela());
    item.setTabelas(request.getTabelas() != null ? request.getTabelas() : List.of());
    item.setLabel(buildLabel(request));
    item.setStatus(SyncQueueItemStatus.PENDING);
    item.setCreatedAt(LocalDateTime.now());

    return repository.save(item);
  }

  public void remove(String usuario, String itemId) {
    SyncQueueItem item = requireOwnedItem(usuario, itemId);
    if (item.getStatus() == SyncQueueItemStatus.RUNNING) {
      throw new IllegalStateException("Não é possível remover um item em execução.");
    }
    repository.delete(item);
  }

  public void clear(String usuario) {
    List<SyncQueueItem> items = repository.findByUsuarioOrderByCreatedAtAsc(usuario);
    for (SyncQueueItem item : items) {
      if (item.getStatus() != SyncQueueItemStatus.RUNNING) {
        repository.delete(item);
      }
    }
  }

  public void start(String usuario, String token) {
    if (runningUsers.contains(usuario)) {
      return;
    }

    if (!repository.existsByUsuarioAndStatus(usuario, SyncQueueItemStatus.PENDING)) {
      return;
    }

    runningUsers.add(usuario);
    executor.submit(() -> drainQueue(usuario, token));
  }

  private void drainQueue(String usuario, String token) {
    try {
      syncQueueAuthContext.bind(token);
      while (true) {
        Optional<SyncQueueItem> next = repository.findFirstByUsuarioAndStatusOrderByCreatedAtAsc(
            usuario,
            SyncQueueItemStatus.PENDING);
        if (next.isEmpty()) {
          break;
        }

        SyncQueueItem item = next.get();
        currentItemByUser.put(usuario, item.getId());
        markRunning(item);

        try {
          runner.processItem(token, item);
          markDone(item);
        } catch (Exception e) {
          markError(item, e.getMessage() != null ? e.getMessage() : "Falha ao processar item da fila");
        } finally {
          currentItemByUser.remove(usuario);
        }
      }
    } finally {
      syncQueueAuthContext.clear();
      runningUsers.remove(usuario);
      currentItemByUser.remove(usuario);
    }
  }

  private void markRunning(SyncQueueItem item) {
    item.setStatus(SyncQueueItemStatus.RUNNING);
    item.setStartedAt(LocalDateTime.now());
    item.setFinishedAt(null);
    item.setErrorMessage(null);
    repository.save(item);
  }

  private void markDone(SyncQueueItem item) {
    item.setStatus(SyncQueueItemStatus.DONE);
    item.setFinishedAt(LocalDateTime.now());
    item.setErrorMessage(null);
    repository.save(item);
  }

  private void markError(SyncQueueItem item, String message) {
    item.setStatus(SyncQueueItemStatus.ERROR);
    item.setFinishedAt(LocalDateTime.now());
    item.setErrorMessage(message);
    repository.save(item);
  }

  private SyncQueueItem requireOwnedItem(String usuario, String itemId) {
    SyncQueueItem item = repository.findById(itemId)
        .orElseThrow(() -> new IllegalArgumentException("Item da fila não encontrado."));
    if (!item.getUsuario().equals(usuario)) {
      throw new IllegalArgumentException("Item da fila não pertence ao usuário.");
    }
    return item;
  }

  private void validateEnqueueRequest(SyncQueueEnqueueRequest request) {
    if (request.getOperacao() == null) {
      throw new IllegalArgumentException("Operação é obrigatória.");
    }
    if (request.getBase() == null || request.getBase().isBlank()) {
      throw new IllegalArgumentException("Base é obrigatória.");
    }
    if (request.getEsquema() == null || request.getEsquema().isBlank()) {
      throw new IllegalArgumentException("Schema é obrigatório.");
    }
  }

  private boolean hasActiveScope(String usuario, SyncQueueEnqueueRequest request) {
    String scopeKey = scopeKeyForRequest(request);
    List<SyncQueueItem> active = repository.findByUsuarioAndStatusInOrderByCreatedAtAsc(usuario, ACTIVE_STATUSES);
    return active.stream().anyMatch(item -> scopeKeyForItem(item).equals(scopeKey));
  }

  private String scopeKeyForRequest(SyncQueueEnqueueRequest request) {
    String tabela = singleTable(request.getTabela(), request.getTabelas());
    return scopeKey(request.getBase(), request.getEsquema(), tabela, request.getOperacao());
  }

  private String scopeKeyForItem(SyncQueueItem item) {
    String tabela = singleTable(item.getTabela(), item.getTabelas());
    return scopeKey(item.getBaseNome(), item.getSchemaNome(), tabela, item.getOperacao());
  }

  private String singleTable(String tabela, List<String> tabelas) {
    if (tabelas != null && tabelas.size() == 1) {
      return tabelas.get(0);
    }
    return tabela;
  }

  private String scopeKey(String base, String esquema, String tabela, TipoOperacao operacao) {
    if (tabela != null && !tabela.isBlank()) {
      return base + "|" + esquema + "|" + tabela + "|" + operacao.name();
    }
    return base + "|" + esquema + "|" + operacao.name();
  }

  private String buildLabel(SyncQueueEnqueueRequest request) {
    if (request.getLabel() != null && !request.getLabel().isBlank()) {
      return request.getLabel();
    }

    StringBuilder label = new StringBuilder(request.getBase());
    label.append('.').append(request.getEsquema());

    if (request.getTabelas() != null && request.getTabelas().size() == 1) {
      label.append('.').append(shortTableName(request.getTabelas().get(0), request.getEsquema()));
    } else if (request.getTabela() != null && !request.getTabela().isBlank()) {
      label.append('.').append(shortTableName(request.getTabela(), request.getEsquema()));
    }

    return label.toString();
  }

  private String shortTableName(String tabela, String esquema) {
    if (tabela.contains(".")) {
      String tail = tabela.substring(tabela.lastIndexOf('.') + 1);
      return tail.equals(esquema) ? esquema : tail;
    }
    return tabela.equals(esquema) ? esquema : tabela;
  }
}
