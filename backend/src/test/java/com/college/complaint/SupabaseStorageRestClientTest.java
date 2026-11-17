package com.college.complaint;

import com.college.complaint.storage.SupabaseStorageRestClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseStorageRestClientTest {

    private static final String BASE_URL = "https://campus-project.supabase.co";
    private static final String SERVICE_KEY = "private-test-service-role";

    @Test
    void uploadsAndDeletesObjectsUsingThePrivateBucketEndpoints() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseStorageRestClient storage = new SupabaseStorageRestClient(
                builder, BASE_URL, "complaint-images", SERVICE_KEY);
        String path = "complaint-uuid/image-uuid.png";

        server.expect(requestTo(BASE_URL + "/storage/v1/object/complaint-images/" + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SERVICE_KEY))
                .andExpect(header("apikey", SERVICE_KEY))
                .andExpect(header("x-upsert", "false"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andRespond(withSuccess());
        server.expect(requestTo(BASE_URL + "/storage/v1/object/complaint-images"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer " + SERVICE_KEY))
                .andExpect(content().json("{\"prefixes\":[\"" + path + "\"]}"))
                .andRespond(withSuccess());

        storage.upload(path, new byte[]{1, 2, 3}, "image/png");
        storage.delete(path);
        server.verify();
    }

    @Test
    void createsShortLivedSignedUrlAndNormalizesTheSupabasePath() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseStorageRestClient storage = new SupabaseStorageRestClient(
                builder, BASE_URL, "complaint-images", SERVICE_KEY);
        String path = "complaint-uuid/image-uuid.jpg";

        server.expect(requestTo(BASE_URL + "/storage/v1/object/sign/complaint-images/" + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SERVICE_KEY))
                .andExpect(content().json("{\"expiresIn\":300}"))
                .andRespond(withSuccess("{\"signedURL\":\"/object/sign/complaint-images/" + path
                        + "?token=temporary\"}", MediaType.APPLICATION_JSON));

        assertEquals(BASE_URL + "/storage/v1/object/sign/complaint-images/" + path + "?token=temporary",
                storage.createSignedUrl(path, Duration.ofSeconds(300)));
        server.verify();
    }

    @Test
    void sendsNewSecretKeyOnlyAsApiKeyAndNotAsBearerJwt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String newFormatKey = "sb_" + "secret_test-only-not-a-real-credential";
        SupabaseStorageRestClient storage = new SupabaseStorageRestClient(
                builder, BASE_URL, "complaint-images", newFormatKey);
        String path = "complaint-uuid/image-uuid.png";

        server.expect(requestTo(BASE_URL + "/storage/v1/object/complaint-images/" + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", newFormatKey))
                .andExpect(request -> assertFalse(
                        request.getHeaders().containsKey("Authorization")))
                .andRespond(withSuccess());

        storage.upload(path, new byte[]{1, 2, 3}, "image/png");
        server.verify();
    }
}
