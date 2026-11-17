package com.college.complaint.storage;

import java.time.Duration;

/** Backend-only access to the private Supabase Storage bucket. */
public interface SupabaseStorageClient {

    void upload(String objectPath, byte[] bytes, String contentType);

    void delete(String objectPath);

    String createSignedUrl(String objectPath, Duration expiresIn);
}
