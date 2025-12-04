package com.guardtime.trace4eo.provenance.container.model;

import java.util.SequencedSet;

public record FilesInfo(
    SequencedSet<FileHashInfo> files,
    FilesContext filesContext
) implements RecordComponent {
}
