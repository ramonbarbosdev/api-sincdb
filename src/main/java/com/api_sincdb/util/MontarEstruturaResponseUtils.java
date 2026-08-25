package com.api_sincdb.util;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.api_sincdb.domain.operacao.dto.CategoriaDDLDTO;
import com.api_sincdb.domain.operacao.dto.DDLItemDTO;
import com.api_sincdb.domain.operacao.dto.EstruturaResponse;
import com.api_sincdb.domain.operacao.dto.ResumoDTO;
import com.api_sincdb.domain.operacao.model.EstruturaTabela;

public class MontarEstruturaResponseUtils {

    public static Map<String, List<EstruturaTabela>> montarDetalhesPorCategoria(
            Map<String, List<String>> queries) {

        Map<String, List<EstruturaTabela>> categorias = new LinkedHashMap<>();

        if (queries == null || queries.isEmpty()) {
            return categorias;
        }

        for (Map.Entry<String, List<String>> entry : queries.entrySet()) {

            String categoria = entry.getKey();
            List<String> listaSql = entry.getValue();

            if (listaSql == null || listaSql.isEmpty()) {
                continue;
            }

            List<EstruturaTabela> itens = new ArrayList<>();

            for (String sql : listaSql) {

                if (sql == null || sql.isBlank())
                    continue;

                EstruturaTabela et = new EstruturaTabela(
                        extrairObjeto(sql),
                        categoria);

                et.setQuerys(sql);
                itens.add(et);
            }

            categorias.put(categoria, itens);
        }

        return categorias;
    }

    private static String extrairObjeto(String sql) {

        if (sql == null)
            return "Objeto SQL";

        String s = sql.trim();

        if (s.contains("|")) {
            String[] partes = s.split("\\|", 2);
            return partes[0] + "." + partes[1];
        }

        String upper = s.toUpperCase();

        // CREATE SEQUENCE schema.seq
        if (upper.startsWith("CREATE SEQUENCE")) {
            return s.split("\\s+")[4];
        }

        // CREATE TABLE schema.table
        if (upper.startsWith("CREATE TABLE")) {
            return s.split("\\s+")[2];
        }

        // DROP VIEW schema.view
        if (upper.startsWith("DROP VIEW")) {
            return s.split("\\s+")[4];
        }

        // ALTER TABLE schema.table ADD CONSTRAINT ... FOREIGN KEY
        if (upper.startsWith("ALTER TABLE") && upper.contains("FOREIGN KEY")) {
            return s.split("\\s+")[2];
        }

        // fallback seguro
        return "Objeto SQL";
    }

    public static EstruturaResponse montarEstruturaResponse(
            Map<String, Object> resultado,
            String base,
            String esquema) {

        EstruturaResponse response = new EstruturaResponse();
        response.setSucesso(Boolean.TRUE.equals(resultado.get("sucesso")));
        response.setBase(base);
        response.setEsquema(esquema);
        response.setGeradoEm(LocalDateTime.now());

        List<CategoriaDDLDTO> categorias = new ArrayList<>();
        int ordem = 1;

        for (Map.Entry<String, Object> entry : resultado.entrySet()) {

            if ("sucesso".equals(entry.getKey())) {
                continue;
            }

            if (!(entry.getValue() instanceof List<?> lista) || lista.isEmpty()) {
                continue;
            }

            if (!(lista.get(0) instanceof EstruturaTabela)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            List<EstruturaTabela> estruturas = (List<EstruturaTabela>) lista;

            CategoriaDDLDTO categoria = new CategoriaDDLDTO();
            categoria.setId(entry.getKey().toUpperCase().replace(" ", "_"));
            categoria.setTitulo(entry.getKey());
            categoria.setIcone(definirIcone(entry.getKey()));
            categoria.setOrdem(ordem++);

            List<DDLItemDTO> items = estruturas.stream().map(estrutura -> {

                DDLItemDTO item = new DDLItemDTO();
                item.setId(UUID.randomUUID().toString());

                // mapeamento correto
                item.setObjeto(estrutura.getTabela());
                item.setTipo(estrutura.getAcao());
                item.setSql(estrutura.getQuerys());

                boolean perigoso = estrutura.getQuerys() != null &&
                        (estrutura.getQuerys().toUpperCase().startsWith("DROP")
                                || estrutura.getQuerys().toUpperCase().contains("CASCADE"));

                item.setPerigoso(perigoso);
                item.setExecutavel(estrutura.getErro() == null);
                item.setSelecionado(true);

                if (estrutura.getErro() != null && !estrutura.getErro().isBlank()) {
                    item.setAvisos(List.of("Erro detectado: " + estrutura.getErro()));
                } else {
                    item.setAvisos(List.of());
                }

                return item;
            }).toList();

            categoria.setItems(items);
            categoria.setTotal(items.size());
            categoria.setPerigosa(items.stream().anyMatch(DDLItemDTO::isPerigoso));

            categorias.add(categoria);
        }

        response.setCategorias(categorias);
        response.setResumo(montarResumo(categorias));

        return response;
    }

    private static String definirIcone(String categoria) {
        return switch (categoria) {
            case "Criação de Tabelas" -> "pi pi-table";
            case "Chaves Estrangeiras" -> "pi pi-link";
            case "Views" -> "pi pi-eye";
            case "Funções" -> "pi pi-code";
            case "Sequências" -> "pi pi-sort-numeric-up";
            default -> "pi pi-database";
        };
    }

    private static String determinarTipo(String sql) {
        String s = sql.trim().toUpperCase();
        if (s.startsWith("CREATE TABLE"))
            return "CREATE_TABLE";
        if (s.startsWith("ALTER TABLE") && s.contains("FOREIGN KEY"))
            return "ADD_FK";
        if (s.startsWith("DROP VIEW"))
            return "DROP_VIEW";
        if (s.startsWith("CREATE VIEW"))
            return "CREATE_VIEW";
        if (s.startsWith("CREATE SEQUENCE"))
            return "CREATE_SEQUENCE";
        if (s.startsWith("CREATE FUNCTION"))
            return "CREATE_FUNCTION";
        return "SQL";
    }

    private static ResumoDTO montarResumo(List<CategoriaDDLDTO> categorias) {

        ResumoDTO resumo = new ResumoDTO();

        int totalQueries = categorias.stream()
                .mapToInt(c -> c.getItems().size())
                .sum();

        long totalPerigosas = categorias.stream()
                .flatMap(c -> c.getItems().stream())
                .filter(DDLItemDTO::isPerigoso)
                .count();

        resumo.setTotalCategorias(categorias.size());
        resumo.setTotalQueries(totalQueries);
        resumo.setTotalPerigosas((int) totalPerigosas);
        resumo.setPossuiOperacoesPerigosas(totalPerigosas > 0);
        resumo.setPodeExecutar(totalPerigosas == 0);

        resumo.setMensagem(
                totalPerigosas > 0
                        ? "Existem operações destrutivas que exigem confirmação."
                        : "Todas as operações são seguras para execução.");

        return resumo;
    }

}
