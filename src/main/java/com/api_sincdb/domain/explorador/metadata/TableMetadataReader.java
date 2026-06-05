package com.api_sincdb.domain.explorador.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public interface TableMetadataReader {

    Map<String, PostgresMetadataReader.TabelaInfo> lerTabelas(Connection conexao, String schemaFiltro)
            throws SQLException;
}
