package com.api_sincdb.domain.explorador.service;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ExploradorSqlPreviewService {

    public String juntarPreview(List<String> sqlPreview) {
        if (sqlPreview == null || sqlPreview.isEmpty()) {
            return "";
        }
        return String.join("\n", sqlPreview);
    }
}
