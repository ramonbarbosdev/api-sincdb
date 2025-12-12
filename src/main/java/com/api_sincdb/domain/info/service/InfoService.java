package com.api_sincdb.domain.info.service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.pulsar.PulsarProperties.Defaults.SchemaInfo;
import org.springframework.stereotype.Service;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.operacao.service.DatabaseService;
import com.api_sincdb.enums.TipoConexao;

@Service
public class InfoService {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private ConexaoBanco conexaoBanco;

    public Map<String, List<String>> compararBases() {
        List<String> cloud = databaseService.listarBases("mudar", TipoConexao.CLOUD);
        List<String> local = databaseService.listarBases("mudar", TipoConexao.LOCAL);

        Set<String> ambos = cloud.stream()
                .filter(local::contains)
                .collect(Collectors.toSet());

        Set<String> somenteCloud = cloud.stream()
                .filter(b -> !local.contains(b))
                .collect(Collectors.toSet());

        Set<String> somenteLocal = local.stream()
                .filter(b -> !cloud.contains(b))
                .collect(Collectors.toSet());

        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("ambos", new ArrayList<>(ambos));
        result.put("somenteCloud", new ArrayList<>(somenteCloud));
        result.put("somenteLocal", new ArrayList<>(somenteLocal));

        return result;
    }

    public Map<String, List<String>> compararSchemasDaBase(String base) {

        List<String> schemasCloud;
        try {
            schemasCloud = databaseService.obterSchema(base, null, TipoConexao.CLOUD);
        } catch (Exception e) {
            schemasCloud = Collections.emptyList();
        }

        List<String> schemasLocal;
        try {
            schemasLocal = databaseService.obterSchema(base, null, TipoConexao.LOCAL);
        } catch (Exception e) {
            schemasLocal = Collections.emptyList();
        }

        final List<String> localFinal = schemasLocal; 
        final List<String> cloudFinal = schemasCloud;

        Set<String> ambos = cloudFinal.stream()
                .filter(localFinal::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> somenteCloud = cloudFinal.stream()
                .filter(s -> !localFinal.contains(s))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> somenteLocal = localFinal.stream()
                .filter(s -> !cloudFinal.contains(s))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("ambos", new ArrayList<>(ambos));
        result.put("somenteCloud", new ArrayList<>(somenteCloud));
        result.put("somenteLocal", new ArrayList<>(somenteLocal));

        return result;
    }

}
