package com.api_sincdb.domain.operacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.api_sincdb.domain.operacao.model.SyncQueueItem;
import com.api_sincdb.enums.SyncQueueItemStatus;
import com.api_sincdb.enums.TipoOperacao;

public interface SyncQueueItemRepository extends MongoRepository<SyncQueueItem, String> {

    List<SyncQueueItem> findByUsuarioAndStatusInOrderByCreatedAtAsc(
            String usuario,
            List<SyncQueueItemStatus> statuses);

    List<SyncQueueItem> findByUsuarioOrderByCreatedAtAsc(String usuario);

    Optional<SyncQueueItem> findFirstByUsuarioAndStatusOrderByCreatedAtAsc(
            String usuario,
            SyncQueueItemStatus status);

    boolean existsByUsuarioAndStatus(String usuario, SyncQueueItemStatus status);

    boolean existsByUsuarioAndBaseNomeAndSchemaNomeAndOperacaoAndStatusIn(
            String usuario,
            String baseNome,
            String schemaNome,
            TipoOperacao operacao,
            List<SyncQueueItemStatus> statuses);
}
