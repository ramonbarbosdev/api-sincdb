package com.api_sincdb.domain.forum.dto;

import java.util.List;

import com.api_sincdb.domain.forum.model.ForumSystemStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForumMetricasResponse {
    private ForumSystemStatus status;
    private long bugsAbertos;
    private long sugestoesAbertas;
    private List<ForumPostResponse> postsDestaque;
}
