package com.guardtime.trace4eo.provenance.signing;

import com.guardtime.trace4eo.provenance.ProvenanceSignature;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

public final class SignatureUtil {

    private SignatureUtil() {
    }

    public static UUID createUuid(ProvenanceSignature signature) {
        return createUuid(signature.bytes(), signature.signingTime().toEpochMilli());
    }

    // https://www.rfc-editor.org/rfc/rfc9562.html#name-uuid-version-8
    public static UUID createUuid(byte[] signatureBytes, long signingTime) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // set custom_a
            byte[] signingTimeBytes = toBytes(signingTime);
            baos.writeBytes(signingTimeBytes);

            // set custom_b and custom_c
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            baos.write(md.digest(signatureBytes), 0, 10);
            byte[] byteArray = baos.toByteArray();

            // set ver
            byteArray[6] &= 0b00001111;
            byteArray[6] |= 0b10000000;

            // set var
            byteArray[8] &= 0b00111111;
            byteArray[8] |= 0b10000000;

            // Ensure exactly 16 bytes
            ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOf(byteArray, 16));
            long msb = bb.getLong();
            long lsb = bb.getLong();
            return new UUID(msb, lsb);
        } catch (Exception e) {
            // TODO exception handling
            throw new RuntimeException(e);
        }
    }

    private static byte[] toBytes(long value) {
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            bytes[5 - i] = (byte) (value >>> (i * 8));
        }
        return bytes;
    }
}
