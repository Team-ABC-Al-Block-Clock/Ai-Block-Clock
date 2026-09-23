package com.aiblockclock.domain.node;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.support.paging.CursorPage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeDefinitionService {
    private final NodeDefinitionReader nodeReader;
    private final NodeDefinitionWriter nodeWriter;
    private final NodeSpecAssembler specAssembler;
    private final NodeDefinitionValidator validator;
    private final RefFileValidator refFileValidator;
    private final RefFileReader refFileReader;
    private final RefFileWriter refFileWriter;
    private final RefFileStore refFileStore;

    public NodeDefinition create(UserId requester, String name, Prompt prompt, AiModel model) {
        NodeSpec spec = specAssembler.assemble(name, prompt, model);

        return nodeWriter.append(NodeDefinition.create(NodeDefinitionId.newId(), requester, spec, prompt));
    }

    public NodeDefinition get(NodeDefinitionId id, UserId requester) {
        NodeDefinition node = nodeReader.read(id);
        NodeDefinitionValidator.ensureOwner(node.getOwner(), requester);

        return node;
    }

    public CursorPage<NodeDefinitionSummary> getMine(UserId requester, String keyword, String cursor, int size) {
        return nodeReader.readMine(requester, keyword, cursor, size);
    }

    @Transactional
    public NodeDefinition update(NodeDefinitionId id, UserId requester, String name, Prompt prompt, AiModel model) {
        validator.ensureEditable(id, requester);

        return nodeWriter.update(id, specAssembler.assemble(name, prompt, model), prompt);
    }

    public List<RefFile> getRefFiles(NodeDefinitionId id, UserId requester) {
        NodeDefinitionValidator.ensureOwner(nodeReader.read(id).getOwner(), requester);

        return refFileReader.read(id);
    }

    @Transactional
    public RefFile attachRefFile(NodeDefinitionId id, UserId requester, UploadedFile upload) {
        validator.ensureEditable(id, requester);
        refFileValidator.validate(upload, refFileReader.read(id));

        return refFileWriter.append(id, refFileStore.write(id, upload));
    }

    @Transactional
    public void detachRefFile(NodeDefinitionId id, UserId requester, RefFileId fileId) {
        validator.ensureEditable(id, requester);

        refFileWriter.remove(id, fileId);
    }

    @Transactional
    public void delete(NodeDefinitionId id, UserId requester) {
        validator.ensureDeletable(id, requester);

        nodeWriter.remove(id);
    }
}
