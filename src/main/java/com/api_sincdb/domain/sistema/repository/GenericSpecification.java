package com.api_sincdb.domain.sistema.repository;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class GenericSpecification<T> implements Specification<T> {

    private final String search;
    private final List<String> fields;

    public GenericSpecification(String search, List<String> fields) {
        this.search = search;
        this.fields = fields;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {

        if (search == null || search.isBlank()) {
            return cb.conjunction();
        }

        String term = "%" + search.toLowerCase() + "%";
        List<Predicate> predicates = new ArrayList<>();

        for (String field : fields) {
            predicates.add(cb.like(cb.lower(root.get(field).as(String.class)), term));
        }

        return cb.or(predicates.toArray(new Predicate[0]));
    }
}