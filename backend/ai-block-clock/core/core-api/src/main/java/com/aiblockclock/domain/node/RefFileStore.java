package com.aiblockclock.domain.node;

import com.aiblockclock.domain.file.FileStore;
import com.aiblockclock.domain.file.UploadedFile;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefFileStore {
    private final FileStore fileStore;

    public RefFile write(NodeDefinitionId nodeId, UploadedFile upload) {
        RefFileId id = RefFileId.newId();
        RefFile file = new RefFile(
                id,
                "nodes/" + nodeId.value() + "/" + id.value() + upload.extension(),
                upload.filename(),
                upload.size());

        fileStore.write(file.key(), upload.content());

        return file;
    }
}
