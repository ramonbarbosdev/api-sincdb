package com.api_sincdb.util;

import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class MestreDetalheUtils {

    public static <T, String> void removerItensGenerico(
            String idMaster,
            List<T> itensAtualizados,
            Function<String, List<T>> buscarPersistidos,
            Consumer<String> deletar,
            Function<T, String> extrairId) {

        List<T> itensPersistidos = buscarPersistidos.apply(idMaster);

        for (T itemPersistido : itensPersistidos) {
            boolean aindaExiste = itensAtualizados.stream()
                    .anyMatch(i -> {
                        String idItem = extrairId.apply(i);
                        String idPersistido = extrairId.apply(itemPersistido);
                        return idItem != null && idItem.equals(idPersistido);
                    });

            if (!aindaExiste) {
                String idItemPersistido = extrairId.apply(itemPersistido);
                if (idItemPersistido != null) {
                    deletar.accept(idItemPersistido);
                }
            }
        }
    }

    public static <M, T, ID> void removerItensGenerico2(
            M mestre,
            List<T> itensAtualizados,
            Function<T, ID> extrairId,
            Function<M, List<T>> getItensPersistidos) {

        List<T> itensPersistidos = getItensPersistidos.apply(mestre);

        itensPersistidos.removeIf(itemPersistido -> itensAtualizados.stream()
                .noneMatch(i -> {
                    ID idItem = extrairId.apply(i);
                    ID idPersistido = extrairId.apply(itemPersistido);
                    return idItem != null && idItem.equals(idPersistido);
                }));
    }

    public static <T, ID> void validarItemSequencia(
            Long idMestre,
            String codigo,
            Boolean fl_existe,
            String nmItem) throws Exception {

        if (fl_existe != null && fl_existe) {
            throw new Exception("Codigo sequencial do item " + nmItem + " está repetindo.");
        }
    }

    // public static <M, D, ID> void salvarDetalhesGenerico(
    // ID idMestre,
    // List<D> detalhes,
    // Function<ID, List<D>> findByMestre,
    // Consumer<ID> deleteById,
    // Function<D, ID> getIdDetalhe,
    // BiConsumer<D, ID> setIdMestre,
    // BiConsumer<D, ID> setIdDetalhe,
    // Function<D, D> saveDetalhe,
    // Predicate<D> validacao // <-- validação
    // ) throws Exception {

    // // Remove itens que não estão mais na lista
    // List<D> existentes = findByMestre.apply(idMestre);
    // for (D existente : existentes) {
    // ID existenteId = getIdDetalhe.apply(existente);
    // boolean contem = detalhes.stream()
    // .anyMatch(d -> getIdDetalhe.apply(d).equals(existenteId));
    // if (!contem) {
    // deleteById.accept(existenteId);
    // }
    // }

    // // Salva ou atualiza itens
    // if (detalhes != null) {
    // for (D item : detalhes) {
    // // Validação
    // if (!validacao.test(item)) {
    // throw new Exception("Item inválido: " + item);
    // }

    // setIdMestre.accept(item, idMestre);
    // ID itemId = getIdDetalhe.apply(item);
    // if (itemId == null || (itemId instanceof Number && ((Number)
    // itemId).longValue() == 0L)) {
    // setIdDetalhe.accept(item, null);
    // }
    // saveDetalhe.apply(item);
    // }
    // }
    // }
}
