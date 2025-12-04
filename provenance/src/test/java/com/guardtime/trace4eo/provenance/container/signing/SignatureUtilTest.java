package com.guardtime.trace4eo.provenance.container.signing;

import com.guardtime.trace4eo.provenance.container.model.ProvenanceSignature;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.guardtime.trace4eo.provenance.container.io.TestUtils.SIGNATURE_1;
import static com.guardtime.trace4eo.provenance.container.io.TestUtils.readSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SignatureUtilTest {

    @Test
    void createUuid() {
        ProvenanceSignature signature = readSignature(SIGNATURE_1);
        UUID uuidFromSignature = SignatureUtil.createUuid(signature);
        assertIsValidUUIDv8(uuidFromSignature);
        assertUuidTimeMatchesSignatureTime(uuidFromSignature, signature);

        UUID validUuidv8FromRfc = UUID.fromString("2489E9AD-2EE2-8E00-8EC9-32D5F69181C0");
        assertIsValidUUIDv8(validUuidv8FromRfc);
    }

    private void assertIsValidUUIDv8(UUID uuid) {
        assertEquals(8, uuid.version());
        assertEquals(2, uuid.variant());
    }

    private void assertUuidTimeMatchesSignatureTime(UUID uuid, ProvenanceSignature signature) {
        long epochSecondsFromSignature = signature.signingTime().toEpochMilli();
        long epochSecondsFromUuid = uuid.getMostSignificantBits() >>> 16; // get 6 highest bytes
        assertEquals(epochSecondsFromSignature, epochSecondsFromUuid);
    }
}
