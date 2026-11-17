package com.college.complaint.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.Optional;

/** Creates opaque, signed tracking references without adding a tracking column. */
@Service
public class ComplaintTrackingReferenceService {

    private static final byte FORMAT_VERSION = 1;
    private static final int PAYLOAD_LENGTH = 1 + Long.BYTES + Long.BYTES;
    private static final int SIGNATURE_LENGTH = 32;
    private static final int REFERENCE_LENGTH = PAYLOAD_LENGTH + SIGNATURE_LENGTH;
    private static final String MAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec signingKey;

    public ComplaintTrackingReferenceService(
            @Value("${app.complaint-tracking.secret-base64}") String encodedSecret) {
        final byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("COMPLAINT_TRACKING_SECRET_BASE64 must be valid Base64", exception);
        }
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "COMPLAINT_TRACKING_SECRET_BASE64 must decode to at least 32 bytes");
        }
        signingKey = new SecretKeySpec(secret, MAC_ALGORITHM);
    }

    public String generate(UUID complaintId) {
        byte[] payload = payloadFor(complaintId);
        byte[] signature = sign(payload);
        byte[] referenceBytes = ByteBuffer.allocate(REFERENCE_LENGTH)
                .put(payload)
                .put(signature)
                .array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(referenceBytes);
    }

    /** Constant-time signature check used by future tracking lookups and current tests. */
    public boolean verifies(String reference, UUID complaintId) {
        if (reference == null || complaintId == null) {
            return false;
        }
        final byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(reference);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (decoded.length != REFERENCE_LENGTH) {
            return false;
        }

        byte[] payload = Arrays.copyOfRange(decoded, 0, PAYLOAD_LENGTH);
        byte[] suppliedSignature = Arrays.copyOfRange(decoded, PAYLOAD_LENGTH, REFERENCE_LENGTH);
        if (!MessageDigest.isEqual(payload, payloadFor(complaintId))) {
            return false;
        }
        return MessageDigest.isEqual(suppliedSignature, sign(payload));
    }

    /** Resolves a signed reference after verifying its version and HMAC. */
    public Optional<UUID> resolve(String reference) {
        if (reference == null) return Optional.empty();
        final byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(reference);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (decoded.length != REFERENCE_LENGTH) return Optional.empty();
        byte[] payload = Arrays.copyOfRange(decoded, 0, PAYLOAD_LENGTH);
        byte[] signature = Arrays.copyOfRange(decoded, PAYLOAD_LENGTH, REFERENCE_LENGTH);
        if (payload[0] != FORMAT_VERSION || !MessageDigest.isEqual(signature, sign(payload))) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.get();
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }

    private byte[] payloadFor(UUID complaintId) {
        return ByteBuffer.allocate(PAYLOAD_LENGTH)
                .put(FORMAT_VERSION)
                .putLong(complaintId.getMostSignificantBits())
                .putLong(complaintId.getLeastSignificantBits())
                .array();
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to generate complaint tracking reference", exception);
        }
    }
}
