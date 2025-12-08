package com.api_sincdb.domain.empresa.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api_sincdb.domain.empresa.model.PlanoAssinatura;
import com.api_sincdb.domain.empresa.repository.PlanoAssinaturaRepository;


@Service
public class PlanoAssinaturaService {

    @Autowired
    private PlanoAssinaturaRepository repository;

    @Transactional(rollbackFor = Exception.class)
    public PlanoAssinatura salvar(PlanoAssinatura objeto) throws Exception {

        objeto = repository.save(objeto);

        return objeto;
    }


}
