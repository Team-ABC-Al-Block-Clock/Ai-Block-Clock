package com.aiblockclock.domain.workflow;

import com.aiblockclock.storage.graph.GraphEntity;
import com.aiblockclock.storage.graph.GraphRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GraphWriter {
    private final GraphRepository graphRepository;

    @Transactional
    public void append(GraphId graphId) {
        graphRepository.save(new GraphEntity(graphId.value()));
    }

    @Transactional
    public void remove(GraphId graphId) {
        graphRepository.deleteById(graphId.value());
    }
}
