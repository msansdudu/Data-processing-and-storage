package ru.nsu.chebotareva.http;

import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpFactory {
    private HttpFactory() {}

    public static HttpClient create(Duration connectionTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectionTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
