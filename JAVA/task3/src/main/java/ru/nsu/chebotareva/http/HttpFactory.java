package ru.nsu.chebotareva.http;

import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpFactory {
    private HttpFactory() {}

    public static HttpClient create(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}
