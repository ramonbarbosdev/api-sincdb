package com.api_sincdb.domain.explorador.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public interface IndexMetadataReader {

    void carregarIndices(Connection conexao, String schemaFiltro,
            Map<String, PostgresMetadataReader.TabelaInfo> tabelas) throws SQLException;
}
