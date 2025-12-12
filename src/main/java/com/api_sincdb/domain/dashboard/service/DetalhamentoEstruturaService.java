package com.api_sincdb.domain.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.pulsar.PulsarProperties.Defaults.SchemaInfo;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.operacao.service.DatabaseService;

@Service
public class DetalhamentoEstruturaService {

    @Autowired
    private DatabaseService databaseService;

    // public StatusSchema avaliarStatus(SchemaInfo cloud, SchemaInfo local) {
    //     if (local == null)
    //         return StatusSchema.NAO_SINCRONIZADO;

    //     if (!cloud.equals(local))
    //         return StatusSchema.DESATUALIZADO;

    //     return StatusSchema.SINCRONIZADO;

    // }
}
