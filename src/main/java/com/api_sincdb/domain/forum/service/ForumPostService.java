package com.api_sincdb.domain.forum.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.feedback.model.FeedbackReport;
import com.api_sincdb.domain.feedback.repository.FeedbackReportRepository;
import com.api_sincdb.domain.forum.dto.ForumPostCreateRequest;
import com.api_sincdb.domain.forum.dto.ForumPostResponse;
import com.api_sincdb.domain.forum.dto.ForumPostUpdateRequest;
import com.api_sincdb.domain.forum.model.ForumPost;
import com.api_sincdb.domain.forum.repository.ForumCurtidaRepository;
import com.api_sincdb.domain.forum.repository.ForumPostRepository;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.enums.ForumPostStatus;
import com.api_sincdb.enums.TipoFeedback;

@Service
public class ForumPostService {

    private static final int DESTAQUE_MIN_CURTIDAS = 3;
    private static final int DESTAQUE_TOP_N = 3;

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private ForumCurtidaRepository curtidaRepository;

    @Autowired
    private FeedbackReportRepository legacyRepository;

    @Autowired
    private ForumContextHelper context;

    public ForumPost criar(ForumPostCreateRequest request) {
        validarConteudo(request.getTipo(), request.getTitulo(), request.getDescricao());

        Usuario autor = context.buscarUsuarioAtual().orElse(null);
        String idAutor = context.chaveAutor(autor);

        ForumPost post = new ForumPost();
        post.setTipo(request.getTipo());
        post.setTitulo(request.getTitulo().trim());
        post.setDescricao(request.getDescricao().trim());
        post.setIdUsuario(idAutor);
        post.setNomeUsuario(context.nomeExibicao(autor, idAutor));
        post.setStatusPost(ForumPostStatus.ABERTO);
        post.setCurtidasCount(0);
        post.setCreatedAt(LocalDateTime.now());

        return postRepository.save(post);
    }

    public List<ForumPostResponse> listar(String filtro, String ordenacao, String role) {
        migrarLegadoSeNecessario();

        List<ForumPost> posts = new ArrayList<>(postRepository.findAllByOrderByCreatedAtDesc());
        posts = filtrarPorTipo(posts, filtro);
        posts = ordenar(posts, ordenacao);

        Set<String> destaqueIds = calcularIdsDestaque(posts);
        String idAtual = context.idUsuarioAtual();
        Set<String> curtidos = buscarPostIdsCurtidos(idAtual, posts);

        return posts.stream()
                .map(p -> toResponse(p, role, destaqueIds.contains(p.getId()), curtidos.contains(p.getId())))
                .collect(Collectors.toList());
    }

    public ForumPostResponse atualizar(String id, ForumPostUpdateRequest request, String role) {
        ForumPost post = buscarObrigatorio(id);
        if (!context.podeGerenciarPost(post, role)) {
            throw new IllegalStateException("Sem permissão para editar este post.");
        }

        if (request.getTipo() != null) {
            post.setTipo(request.getTipo());
        }
        if (request.getTitulo() != null || request.getDescricao() != null) {
            validarConteudo(
                    post.getTipo(),
                    request.getTitulo() != null ? request.getTitulo() : post.getTitulo(),
                    request.getDescricao() != null ? request.getDescricao() : post.getDescricao());
        }
        if (request.getTitulo() != null) {
            post.setTitulo(request.getTitulo().trim());
        }
        if (request.getDescricao() != null) {
            post.setDescricao(request.getDescricao().trim());
        }
        if (request.getStatusPost() != null) {
            if (!context.isDev(role)) {
                throw new IllegalStateException("Apenas dev pode alterar status do post.");
            }
            post.setStatusPost(request.getStatusPost());
        }

        ForumPost saved = postRepository.save(post);
        return toResponse(saved, role, isEmDestaque(saved), curtidoPorMim(saved.getId()));
    }

    public void excluir(String id, String role) {
        ForumPost post = buscarObrigatorio(id);
        if (!context.podeGerenciarPost(post, role)) {
            throw new IllegalStateException("Sem permissão para excluir este post.");
        }
        curtidaRepository.deleteByPostId(id);
        postRepository.deleteById(id);
    }

    public ForumPostResponse patchDestaque(String id, boolean destacar, String role) {
        if (!context.isDev(role)) {
            throw new IllegalStateException("Acesso negado.");
        }
        ForumPost post = buscarObrigatorio(id);
        if (destacar) {
            post.setDestaqueManual(true);
            post.setDestaqueExcluido(false);
        } else {
            post.setDestaqueManual(false);
            post.setDestaqueExcluido(true);
        }
        ForumPost saved = postRepository.save(post);
        Set<String> destaqueIds = calcularIdsDestaque(postRepository.findAll());
        return toResponse(
                saved,
                role,
                destaqueIds.contains(saved.getId()),
                curtidoPorMim(saved.getId()));
    }

    public ForumPostResponse patchStatus(String id, ForumPostStatus status, String role) {
        if (!context.isDev(role)) {
            throw new IllegalStateException("Acesso negado.");
        }
        ForumPost post = buscarObrigatorio(id);
        validarStatusParaTipo(post, status);
        post.setStatusPost(status);
        ForumPost saved = postRepository.save(post);
        return toResponse(saved, role, isEmDestaque(saved), curtidoPorMim(saved.getId()));
    }

    public long contarAbertos(TipoFeedback tipo) {
        migrarLegadoSeNecessario();
        return postRepository.findAll().stream()
                .filter(p -> p.getTipo() == tipo)
                .filter(this::isPostAberto)
                .count();
    }

    private boolean isPostAberto(ForumPost post) {
        if (post.getTipo() == TipoFeedback.BUG) {
            return post.getStatusPost() != ForumPostStatus.RESOLVIDO;
        }
        if (post.getTipo() == TipoFeedback.SUGESTAO) {
            return post.getStatusPost() != ForumPostStatus.IMPLEMENTADO;
        }
        return true;
    }

    private void validarStatusParaTipo(ForumPost post, ForumPostStatus status) {
        if (post.getTipo() == TipoFeedback.BUG) {
            if (status != ForumPostStatus.RESOLVIDO && status != ForumPostStatus.ABERTO) {
                throw new IllegalArgumentException("Status inválido para bug.");
            }
            return;
        }
        if (post.getTipo() == TipoFeedback.SUGESTAO) {
            if (status != ForumPostStatus.IMPLEMENTADO && status != ForumPostStatus.ABERTO) {
                throw new IllegalArgumentException("Status inválido para sugestão.");
            }
        }
    }

    public List<ForumPostResponse> topDestaques(int limit, String role) {
        migrarLegadoSeNecessario();
        List<ForumPost> posts = postRepository.findAll().stream()
                .sorted(Comparator.comparingInt(ForumPost::getCurtidasCount).reversed()
                        .thenComparing(ForumPost::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());

        Set<String> destaqueIds = calcularIdsDestaque(postRepository.findAll());
        String idAtual = context.idUsuarioAtual();
        Set<String> curtidos = buscarPostIdsCurtidos(idAtual, posts);

        return posts.stream()
                .map(p -> toResponse(p, role, destaqueIds.contains(p.getId()), curtidos.contains(p.getId())))
                .collect(Collectors.toList());
    }

    public ForumPostResponse mapResponse(ForumPost post, String role) {
        return toResponse(post, role, isEmDestaque(post), curtidoPorMim(post.getId()));
    }

    public ForumPost buscarObrigatorio(String id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post não encontrado."));
    }

    void sincronizarContadorCurtidas(String postId) {
        ForumPost post = buscarObrigatorio(postId);
        post.setCurtidasCount((int) curtidaRepository.countByPostId(postId));
        postRepository.save(post);
    }

    private void migrarLegadoSeNecessario() {
        if (postRepository.count() > 0) {
            return;
        }
        List<FeedbackReport> legado = legacyRepository.findAll();
        if (legado.isEmpty()) {
            return;
        }
        for (FeedbackReport item : legado) {
            ForumPost post = new ForumPost();
            post.setId(item.getId());
            post.setTipo(item.getTipo());
            post.setTitulo(item.getTitulo());
            post.setDescricao(item.getDescricao());
            post.setIdUsuario(item.getUsuario());
            post.setNomeUsuario(item.getNomeUsuario());
            post.setStatusPost(ForumPostStatus.ABERTO);
            post.setCurtidasCount(0);
            post.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt() : LocalDateTime.now());
            preencherNomeSeAusente(post);
            postRepository.save(post);
        }
    }

    private List<ForumPost> filtrarPorTipo(List<ForumPost> posts, String filtro) {
        if (filtro == null || filtro.isBlank() || "todos".equalsIgnoreCase(filtro)) {
            return posts;
        }
        if ("bug".equalsIgnoreCase(filtro)) {
            return posts.stream().filter(p -> p.getTipo() == TipoFeedback.BUG).collect(Collectors.toList());
        }
        if ("sugestao".equalsIgnoreCase(filtro)) {
            return posts.stream().filter(p -> p.getTipo() == TipoFeedback.SUGESTAO).collect(Collectors.toList());
        }
        return posts;
    }

    private List<ForumPost> ordenar(List<ForumPost> posts, String ordenacao) {
        if ("recente".equalsIgnoreCase(ordenacao)) {
            return posts.stream()
                    .sorted(Comparator.comparing(ForumPost::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());
        }
        return posts.stream()
                .sorted(Comparator.comparingInt(ForumPost::getCurtidasCount).reversed()
                        .thenComparing(ForumPost::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private Set<String> calcularIdsDestaque(List<ForumPost> posts) {
        Set<String> ids = new HashSet<>();
        for (ForumPost p : posts) {
            if (Boolean.TRUE.equals(p.getDestaqueManual())) {
                ids.add(p.getId());
            }
        }
        List<ForumPost> auto = posts.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getDestaqueExcluido()))
                .collect(Collectors.toList());
        auto.stream()
                .filter(p -> p.getCurtidasCount() >= DESTAQUE_MIN_CURTIDAS)
                .forEach(p -> ids.add(p.getId()));
        auto.stream()
                .sorted(Comparator.comparingInt(ForumPost::getCurtidasCount).reversed()
                        .thenComparing(ForumPost::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(DESTAQUE_TOP_N)
                .forEach(p -> ids.add(p.getId()));
        return ids;
    }

    private boolean isEmDestaque(ForumPost post) {
        return calcularIdsDestaque(postRepository.findAll()).contains(post.getId());
    }

    private boolean curtidoPorMim(String postId) {
        String idAtual = context.idUsuarioAtual();
        if (idAtual == null || idAtual.isBlank()) {
            return false;
        }
        return curtidaRepository.findByPostIdAndIdUsuario(postId, idAtual).isPresent();
    }

    private Set<String> buscarPostIdsCurtidos(String idUsuario, List<ForumPost> posts) {
        Set<String> ids = new HashSet<>();
        if (idUsuario == null || idUsuario.isBlank()) {
            return ids;
        }
        for (ForumPost post : posts) {
            if (curtidaRepository.findByPostIdAndIdUsuario(post.getId(), idUsuario).isPresent()) {
                ids.add(post.getId());
            }
        }
        return ids;
    }

    private ForumPostResponse toResponse(ForumPost post, String role, boolean emDestaque, boolean curtidoPorMim) {
        preencherNomeSeAusente(post);
        ForumPostResponse dto = new ForumPostResponse();
        dto.setId(post.getId());
        dto.setTipo(post.getTipo());
        dto.setTitulo(post.getTitulo());
        dto.setDescricao(post.getDescricao());
        dto.setIdUsuario(post.getIdUsuario());
        dto.setNomeUsuario(post.getNomeUsuario());
        dto.setStatusPost(post.getStatusPost());
        dto.setCurtidasCount(post.getCurtidasCount());
        dto.setCurtidoPorMim(curtidoPorMim);
        dto.setEmDestaque(emDestaque);
        dto.setDestaqueManual(Boolean.TRUE.equals(post.getDestaqueManual()));
        dto.setDestaqueExcluido(Boolean.TRUE.equals(post.getDestaqueExcluido()));
        dto.setCreatedAt(post.getCreatedAt());
        boolean podeGerenciar = context.podeGerenciarPost(post, role);
        dto.setPodeEditar(podeGerenciar);
        dto.setPodeExcluir(podeGerenciar);
        return dto;
    }

    private void preencherNomeSeAusente(ForumPost post) {
        if (post == null) {
            return;
        }
        String nomeAtual = post.getNomeUsuario();
        if (nomeAtual != null
                && !nomeAtual.isBlank()
                && !nomeAtual.equals(post.getIdUsuario())
                && !context.pareceIdMongo(nomeAtual)) {
            return;
        }
        Usuario usuario = context.buscarPorChaveArmazenada(post.getIdUsuario()).orElse(null);
        post.setNomeUsuario(context.nomeExibicao(usuario, post.getIdUsuario()));
    }

    private void validarConteudo(TipoFeedback tipo, String titulo, String descricao) {
        if (tipo == null) {
            throw new IllegalArgumentException("Informe o tipo (bug ou sugestão).");
        }
        if (titulo == null || titulo.trim().length() < 3) {
            throw new IllegalArgumentException("O título deve ter pelo menos 3 caracteres.");
        }
        if (descricao == null || descricao.trim().length() < 10) {
            throw new IllegalArgumentException("Descreva com pelo menos 10 caracteres.");
        }
    }
}
