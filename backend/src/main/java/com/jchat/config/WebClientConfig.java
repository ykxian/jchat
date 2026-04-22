package com.jchat.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient llmWebClient(AppProperties appProperties) {
        AppProperties.Llm llm = appProperties.getLlm();
        ConnectionProvider connectionProvider = ConnectionProvider.builder("llm")
                .maxConnections(llm.getMaxConnections())
                .pendingAcquireTimeout(llm.getPendingAcquireTimeout())
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .responseTimeout(llm.getResponseTimeout())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(llm.getConnectTimeout().toMillis()))
                .compress(false);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(Math.toIntExact(llm.getMaxInMemorySize().toBytes())))
                .build();
    }
}
