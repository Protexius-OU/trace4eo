package com.guardtime.traceguard.provenance.container.signing;

import com.guardtime.ksi.unisignature.KSISignature;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.guardtime.traceguard.provenance.container.io.TestUtils.SIGNATURE_1;
import static com.guardtime.traceguard.provenance.container.io.TestUtils.readKsiSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SignatureUtilTest {

    @Test
    void createUuidFromSignature() {
        KSISignature ksiSignature = readKsiSignature(SIGNATURE_1);
        UUID uuidFromSignature = SignatureUtil.createUuidFromSignature(ksiSignature);
        assertIsValidUUIDv8(uuidFromSignature);
        assertUuidTimeMatchesSignatureTime(uuidFromSignature, ksiSignature);

        UUID validUuidv8FromRfc = UUID.fromString("2489E9AD-2EE2-8E00-8EC9-32D5F69181C0");
        assertIsValidUUIDv8(validUuidv8FromRfc);
    }

    private void assertIsValidUUIDv8(UUID uuid) {
        assertEquals(8, uuid.version());
        assertEquals(2, uuid.variant());
    }

    private void assertUuidTimeMatchesSignatureTime(UUID uuid, KSISignature signature) {
        long epochSecondsFromSignature = signature.getAggregationTime().toInstant().getEpochSecond();
        long epochSecondsFromUuid = uuid.getMostSignificantBits() >>> 16; // get 6 highest bytes
        assertEquals(epochSecondsFromSignature, epochSecondsFromUuid);
    }
}
