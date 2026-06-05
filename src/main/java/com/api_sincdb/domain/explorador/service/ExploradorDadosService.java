package com.api_sincdb.domain.explorador.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.explorador.dto.DadosTabelaPaginadoDTO;
import com.api_sincdb.enums.TipoConexao;

@Service
public class ExploradorDadosService {

    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 500;

    private final ConexaoBanco conexaoBanco;
    private final ExploradorAmbienteResolver ambienteResolver;

    public ExploradorDadosService(ConexaoBanco conexaoBanco, ExploradorAmbienteResolver ambienteResolver) {
        this.conexaoBanco = conexaoBanco;
        this.ambienteResolver = ambienteResolver;
    }

    public DadosTabelaPaginadoDTO listarDados(String token, String ambiente, String base, String schema,
            String tabela, int page, int size, String idConexao) throws Exception {
        TipoConexao tipo = ambienteResolver.resolver(ambiente);
        int pagina = Math.max(page, 0);
        int tamanho = normalizarSize(size);
        int offset = pagina * tamanho;

        try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
            String sql = "select * from " + quoteIdent(schema) + "." + quoteIdent(tabela) + " limit ? offset ?";

            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                stmt.setInt(1, tamanho);
                stmt.setInt(2, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    List<String> colunas = new ArrayList<>();

                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        colunas.add(metaData.getColumnLabel(i));
                    }

                    List<Map<String, Object>> registros = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> registro = new LinkedHashMap<>();
                        for (String coluna : colunas) {
                            registro.put(coluna, rs.getObject(coluna));
                        }
                        registros.add(registro);
                    }

                    return new DadosTabelaPaginadoDTO(ambiente.toLowerCase(), base, schema, tabela, pagina,
                            tamanho, colunas, registros);
                }
            }
        }
    }

    private int normalizarSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String quoteIdent(String valor) {
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }
}
