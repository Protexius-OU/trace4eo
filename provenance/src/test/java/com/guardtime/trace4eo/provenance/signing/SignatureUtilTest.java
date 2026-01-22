package com.guardtime.trace4eo.provenance.signing;

import com.guardtime.trace4eo.provenance.ProvenanceSignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.guardtime.trace4eo.provenance.io.TestUtils.TEST_BYTES_1;
import static com.guardtime.trace4eo.provenance.io.TestUtils.sign;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SignatureUtilTest {

    private static ProvenanceSignature signature;

    @BeforeAll
    static void signTestData() {
        signature = sign(TEST_BYTES_1);
    }

    @Test
    void createUuid() {
        UUID uuidFromSignature = SignatureUtil.createUuid(signature);
        assertIsValidUUIDv8(uuidFromSignature);
        assertUuidTimeMatchesSignatureTime(uuidFromSignature, signature);
    }

    @Test
    void createUuidFromRfcExample() {
        UUID validUuidv8FromRfc = UUID.fromString("2489E9AD-2EE2-8E00-8EC9-32D5F69181C0");
        assertIsValidUUIDv8(validUuidv8FromRfc);
    }

    @Test
    void createUuidIsDeterministic() {
        UUID uuid1 = SignatureUtil.createUuid(signature);
        UUID uuid2 = SignatureUtil.createUuid(signature);
        assertEquals(uuid1, uuid2);
    }

    private void assertIsValidUUIDv8(UUID uuid) {
        assertEquals(8, uuid.version());
        assertEquals(2, uuid.variant());
    }

    private void assertUuidTimeMatchesSignatureTime(UUID uuid, ProvenanceSignature signature) {
        long epochSecondsFromSignature = signature.signingTime().toEpochMilli();
        long epochSecondsFromUuid = uuid.getMostSignificantBits() >>> 16;
        assertEquals(epochSecondsFromSignature, epochSecondsFromUuid);
    }
}
