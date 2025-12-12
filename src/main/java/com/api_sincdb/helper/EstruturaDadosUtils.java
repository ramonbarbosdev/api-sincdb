package com.api_sincdb.helper;

import java.sql.Connection;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.dashboard.service.SincronizacaoSchemaService;
import com.api_sincdb.enums.TipoConexao;
import com.api_sincdb.enums.TipoOperacao;
import com.api_sincdb.util.UtilsSync;
import org.apache.commons.lang3.tuple.Pair;

@Component
public class EstruturaDadosUtils {

    @Autowired
    private SincronizacaoSchemaService sincronizacaoSchemaService;

    @Autowired
    private UtilsSync utilsSync;

    @Autowired
    private ConexaoBanco conexaoBanco;

    public Pair<Connection, Connection> abrirConexoes(String database, String token) throws Exception {
        Connection cloud = conexaoBanco.abrirConexao(database, TipoConexao.CLOUD, token);
        Connection local = conexaoBanco.abrirConexao(database, TipoConexao.LOCAL, token);
        return Pair.of(cloud, local);
    }

    public void finalizarErro(String database,String esquema, String usuario, Map<String, Object> response, Exception e, TipoOperacao tipo) {
        sincronizacaoSchemaService.finalizarErro(database,esquema, usuario, e.getMessage(),tipo);
        utilsSync.tratarErroSincronizacao(response, e);
    }

    public void finalizarCancelado(String database,String esquema, String usuario, Map<String, Object> response, Exception e,TipoOperacao tipo) {

        sincronizacaoSchemaService.finalizarCancelado(database, esquema, usuario, "Sincronização cancelada pelo usuário.",tipo);
        utilsSync.tratarErroCancelamento(response, e);
        // Thread.currentThread().interrupt();
    }
}
