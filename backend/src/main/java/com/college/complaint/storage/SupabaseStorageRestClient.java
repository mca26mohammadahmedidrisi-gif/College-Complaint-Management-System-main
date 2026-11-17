package com.college.complaint.storage;

import com.college.complaint.exception.StorageServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** Calls the Supabase Storage REST API using server-side credentials only. */
@Component
public class SupabaseStorageRestClient implements SupabaseStorageClient {

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String bucket;
    private final String serviceRoleKey;

    public SupabaseStorageRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${supabase.storage.url:}") String supabaseUrl,
            @Value("${supabase.storage.bucket:complaint-images}") String bucket,
            @Value("${supabase.storage.service-role-key:}") String serviceRoleKey) {
        this.restClient = restClientBuilder.build();
        this.supabaseUrl = supabaseUrl;
        this.bucket = bucket;
        this.serviceRoleKey = serviceRoleKey;
    }

    @Override
    public void upload(String objectPath, byte[] bytes, String contentType) {
        requireConfiguration();
        try {
            restClient.post()
                    .uri(storageUri(false, objectPath))
                    .headers(this::addCredentials)
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("x-upsert", "false")
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new StorageServiceException("Supabase Storage upload failed", exception);
        }
    }

    @Override
    public void delete(String objectPath) {
        requireConfiguration();
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri(storageUri(false, null))
                    .headers(this::addCredentials)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new DeleteRequest(List.of(objectPath)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new StorageServiceException("Supabase Storage cleanup failed", exception);
        }
    }

    @Override
    public String createSignedUrl(String objectPath, Duration expiresIn) {
        requireConfiguration();
        try {
            SignedUrlResponse response = restClient.post()
                    .uri(storageUri(true, objectPath))
                    .headers(this::addCredentials)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignRequest(expiresIn.toSeconds()))
                    .retrieve()
                    .body(SignedUrlResponse.class);
            if (response == null || response.signedURL() == null || response.signedURL().isBlank()) {
                throw new StorageServiceException("Supabase Storage returned no signed URL");
            }
            return normalizeSignedUrl(response.signedURL());
        } catch (StorageServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new StorageServiceException("Supabase Storage could not create a signed URL", exception);
        }
    }

    private URI storageUri(boolean signedUrl, String objectPath) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(trimTrailingSlash(supabaseUrl))
                .path("/storage/v1/object/");
        if (signedUrl) {
            builder.pathSegment("sign");
        }
        builder.pathSegment(bucket);
        if (objectPath != null) {
            builder.path("/").path(objectPath);
        }
        return builder.build().encode().toUri();
    }

    private String normalizeSignedUrl(String signedUrl) {
        URI base = URI.create(trimTrailingSlash(supabaseUrl));
        URI signed = URI.create(signedUrl);
        if (signed.isAbsolute()) {
            if (!base.getScheme().equalsIgnoreCase(signed.getScheme())
                    || !base.getHost().equalsIgnoreCase(signed.getHost())
                    || base.getPort() != signed.getPort()) {
                throw new StorageServiceException("Supabase Storage returned an unexpected signed URL host");
            }
            return signed.toString();
        }
        String path = signed.getRawPath();
        String storagePath = path.startsWith("/storage/v1/") ? path : "/storage/v1" + path;
        return base.resolve(storagePath + (signed.getRawQuery() == null ? "" : "?" + signed.getRawQuery()))
                .toString();
    }

    private void addCredentials(HttpHeaders headers) {
        headers.set("apikey", serviceRoleKey);
        // New Supabase secret keys are opaque API keys, not JWTs. Sending one as a
        // Bearer token makes Supabase try to parse it as a JWT and reject the call.
        // Preserve the Authorization header for legacy JWT-based service_role keys.
        if (!serviceRoleKey.startsWith("sb_secret_")) {
            headers.setBearerAuth(serviceRoleKey);
        }
    }

    private void requireConfiguration() {
        if (supabaseUrl == null || supabaseUrl.isBlank()
                || bucket == null || bucket.isBlank()
                || serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new StorageServiceException("Supabase Storage is not configured");
        }
        try {
            URI uri = URI.create(supabaseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Supabase URL must use HTTPS");
            }
        } catch (IllegalArgumentException exception) {
            throw new StorageServiceException("Supabase Storage configuration is invalid", exception);
        }
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private record DeleteRequest(List<String> prefixes) { }

    private record SignRequest(long expiresIn) { }

    private record SignedUrlResponse(String signedURL) { }
}
