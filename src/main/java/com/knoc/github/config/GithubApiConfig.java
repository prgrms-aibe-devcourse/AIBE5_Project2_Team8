package com.knoc.github.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class GithubApiConfig {

    @Bean
    public RestClient githubRestClient(
            RestClient.Builder builder,
            @Value("${github.token:}") String token) {

        RestClient.Builder b = builder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

        if (token != null && !token.isBlank()) {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return b.build();
    }
}