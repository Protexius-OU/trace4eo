package com.guardtime.traceguard.provenance.container.signing;

import com.guardtime.ksi.exceptions.KSIException;
import com.guardtime.ksi.unisignature.AggregationChainLink;
import com.guardtime.ksi.unisignature.AggregationHashChain;
import com.guardtime.ksi.unisignature.KSISignature;
import com.guardtime.ksi.unisignature.inmemory.InMemoryKsiSignatureFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.BitSet;
import java.util.UUID;

public class SignatureUtil {

    public static byte[] toByteArray(KSISignature signature) throws IOException, KSIException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            signature.writeTo(output);
            return output.toByteArray();
        }
    }

    public KSISignature fromBytes(byte[] bytes) throws KSIException {
        return new InMemoryKsiSignatureFactory().createSignature(new ByteArrayInputStream(bytes));
    }

    // https://www.rfc-editor.org/rfc/rfc9562.html#name-uuid-version-8
    public static UUID createUuidFromSignature(KSISignature signature) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // set custom_a
            long signingTime = signature.getAggregationTime().toInstant().getEpochSecond();
            byte[] signingTimeBytes = toBytes(signingTime);
            baos.writeBytes(signingTimeBytes);

            // set custom_b and custom_c
            byte[] arr = getSignatureBytes(signature);
            reverseByteArray(arr);  // To big-endian
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            baos.write(md.digest(arr), 0, 10);
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

    private static byte[] getSignatureBytes(KSISignature signature) {
        BitSet bitSet = new BitSet();
        int bitCounter = 0;
        for (AggregationHashChain aggregationHashChain : signature.getAggregationHashChains()) {
            for (AggregationChainLink chainLink : aggregationHashChain.getChainLinks()) {
                if (!chainLink.isLeft()) {
                    bitSet.set(bitCounter);
                }
                bitCounter++;
            }
        }
        bitSet.set(bitCounter); // Set last bit to indicate the end
        return bitSet.toByteArray();
    }

    private static void reverseByteArray(byte[] arr) {
        int last = arr.length - 1;
        for (int i = 0; i < arr.length / 2; i++) {
            byte tmp = arr[i];
            arr[i] = arr[last - i];
            arr[last - i] = tmp;
        }
    }
}
