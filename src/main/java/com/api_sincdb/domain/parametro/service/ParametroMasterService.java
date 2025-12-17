package com.api_sincdb.domain.parametro.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.api_sincdb.domain.parametro.model.ParametroMaster;
import com.api_sincdb.domain.parametro.repository.ParametroMasterRepository;
import com.api_sincdb.domain.sistema.service.ValidacaoService;


@Service
public class ParametroMasterService {

    @Autowired
    private ValidacaoService validacaoService;

    @Autowired
    private ParametroMasterRepository repository;

    public static final Function<ParametroMaster, String> ID_FUNCTION = ParametroMaster::getId;

    public static final Function<ParametroMaster, String> SEQUENCIA_FUNCTION = ParametroMaster::getCodigo;

    @Transactional(rollbackFor = Exception.class)
    public ParametroMaster salvar(ParametroMaster objeto) throws Exception {

        validarObjeto(objeto);

        return repository.save(objeto);
    }

    public void validarObjeto(ParametroMaster objeto) throws Exception {
        validacaoService.validarCodigoExistente(
                ID_FUNCTION.apply(objeto),
                repository.findByCodigo(SEQUENCIA_FUNCTION.apply(objeto)),
                ID_FUNCTION);
    }

    public Map<String, Object> carregarParametros() {
        List<ParametroMaster> parametros = (List<ParametroMaster>) repository.findAll();
        Map<String, Object> mapa = new HashMap<>();
        for (ParametroMaster p : parametros) {
            mapa.put(p.getNomeChave(), p.getValue());
        }
        return mapa;
    }

    public String sequencia() throws Exception {

        String ultimoCodigo = repository.findTopByOrderByCodigoDesc()
                .map(ParametroMaster::getCodigo)
                .orElse("0");

        String sq_sequencia = validacaoService.gerarSequencia(ultimoCodigo);

        return sq_sequencia;
    }

}
