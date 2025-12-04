package com.guardtime.trace4eo.provenance.io.zip;

import com.guardtime.trace4eo.provenance.Container;
import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.io.ContainerWriter;
import com.guardtime.trace4eo.provenance.record.FileHashInfo;
import com.guardtime.trace4eo.provenance.record.FilesContext;
import com.guardtime.trace4eo.provenance.record.ProvenanceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipContainerWriter implements ContainerWriter {

    private static final Logger log = LoggerFactory.getLogger(ZipContainerWriter.class);

    private final ProvenanceJsonMapper provenanceJsonMapper;

    public ZipContainerWriter(ProvenanceJsonMapper provenanceJsonMapper) {
        this.provenanceJsonMapper = provenanceJsonMapper;
    }

    @Override
    public void writeTo(Container container, OutputStream outputStream) throws IOException {
        log.debug("Writing provenance record to output stream: {}", container);
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            addHeadFile(zipOut, container.head());
            addRecordsDirectory(zipOut);
            for (ProvenanceRecord provenanceRecord : container.provenanceRecords()) {
                addRecordStructure(zipOut, provenanceRecord);
            }
        }
    }

    private void addHeadFile(ZipOutputStream zipOut, UUID headId) throws IOException {
        ZipEntry entry = new ZipEntry(ContainerLayout.HEAD_FILE_NAME);
        zipOut.putNextEntry(entry);
        zipOut.write(headId.toString().getBytes(StandardCharsets.UTF_8));
        zipOut.write('\n');
        zipOut.closeEntry();
    }

    private void addRecordsDirectory(ZipOutputStream zipOut) throws IOException {
        ZipEntry entry = new ZipEntry(ContainerLayout.RECORDS_DIR_NAME);
        zipOut.putNextEntry(entry);
        zipOut.closeEntry();
    }

    private void addRecordStructure(ZipOutputStream zipOut, ProvenanceRecord provenanceRecord) throws IOException {
        // Add container directory
        String recordPath = getRecordPath(provenanceRecord.id());
        ZipEntry recordEntry = new ZipEntry(recordPath);
        zipOut.putNextEntry(recordEntry);
        zipOut.closeEntry();

        addManifestJson(zipOut, provenanceRecord);
        addMetaJson(zipOut, provenanceRecord);
        addFilesJson(zipOut, provenanceRecord);
        addFiles(zipOut, provenanceRecord);
    }

    private void addManifestJson(ZipOutputStream zipOut, ProvenanceRecord provenanceRecord) throws IOException {
        byte[] manifestBytes = provenanceJsonMapper.writeValueAsBytes(provenanceRecord.manifest());

        // Add manifest.json to zip
        String manifestJsonPath = getManifestJsonPath(provenanceRecord.id());
        ZipEntry manifestJsonEntry = new ZipEntry(manifestJsonPath);
        zipOut.putNextEntry(manifestJsonEntry);
        zipOut.write(manifestBytes);
        zipOut.closeEntry();

        // Add signature of manifest.json to zip
        String manifestSignaturePath = getManifestSignaturePath(provenanceRecord.id());
        ZipEntry manifestSignatureEntry = new ZipEntry(manifestSignaturePath);
        zipOut.putNextEntry(manifestSignatureEntry);
        byte[] signatureBytes = provenanceJsonMapper.writeValueAsBytes(provenanceRecord.signature());
        zipOut.write(signatureBytes);
        zipOut.closeEntry();
    }

    private void addMetaJson(ZipOutputStream zipOut, ProvenanceRecord provenanceRecord) throws IOException {
        byte[] metaJsonBytes = provenanceJsonMapper.writeValueAsBytes(provenanceRecord.metadata());

        // Add meta.json to zip
        String metaJsonPath = getMetaJsonPath(provenanceRecord.id());
        ZipEntry entry = new ZipEntry(metaJsonPath);
        zipOut.putNextEntry(entry);
        zipOut.write(metaJsonBytes);
        zipOut.closeEntry();
    }

    private void addFilesJson(ZipOutputStream zipOut, ProvenanceRecord provenanceRecord) throws IOException {
        byte[] filesJsonBytes = provenanceJsonMapper.writeValueAsBytes(provenanceRecord.filesInfo());

        ZipEntry entry = new ZipEntry(getFilesJsonPath(provenanceRecord.id()));
        zipOut.putNextEntry(entry);
        zipOut.write(filesJsonBytes);
        zipOut.closeEntry();
    }

    private void addFiles(ZipOutputStream zipOut, ProvenanceRecord provenanceRecord) throws IOException {
        String filesDir = getContainerFilesPath(provenanceRecord.id());
        FilesContext filesContext = provenanceRecord.filesInfo().filesContext();
        for (FileHashInfo file : provenanceRecord.filesInfo().files()) {
            ZipEntry zipEntry = new ZipEntry(filesDir + file.path().getFileName());
            zipOut.putNextEntry(zipEntry);
            byte[] buffer = new byte[8192];
            int read;
            try (InputStream inputStream = filesContext.getFileContents(file)) {
                while ((read = inputStream.read(buffer)) > 0) {
                    zipOut.write(buffer, 0, read);
                }
            }
            zipOut.closeEntry();
        }
    }

    private String getMetaJsonPath(UUID recordId) {
        return getRecordPath(recordId) + ContainerLayout.METADATA_FILE_NAME;
    }

    private String getFilesJsonPath(UUID recordId) {
        return getRecordPath(recordId) + ContainerLayout.FILES_FILE_NAME;
    }

    private String getManifestJsonPath(UUID recordId) {
        return getRecordPath(recordId) + ContainerLayout.MANIFEST_FILE_NAME;
    }

    private String getManifestSignaturePath(UUID recordId) {
        return getRecordPath(recordId) + ContainerLayout.MANIFEST_SIGNATURE_FILE_NAME;
    }

    private String getContainerFilesPath(UUID recordId) {
        return getRecordPath(recordId) + ContainerLayout.FILES_DIR_NAME;
    }

    private String getRecordPath(UUID containerId) {
        return ContainerLayout.RECORDS_DIR_NAME + containerId + File.separator;
    }
}
