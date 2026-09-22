package com.aiblockclock.client.blockchain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SsafyStoClientTest {
    private MockRestServiceServer server;
    private SsafyStoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sto.ssafy.io");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new SsafyStoClient(restClient, restClient);
    }

    @Test
    void returnsTrueForHealthyBlockchain() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        assertThat(client.isHealthy()).isTrue();
        server.verify();
    }

    @Test
    void preservesFalseResponse() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andRespond(withSuccess("false", MediaType.APPLICATION_JSON));
        assertThat(client.isHealthy()).isFalse();
        server.verify();
    }

    @Test
    void doesNotTreatServerErrorAsFalseResponse() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(client::isHealthy).isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void rejectsEmptyResponse() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andRespond(withSuccess());
        assertThatThrownBy(client::isHealthy).isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void rejectsMalformedResponse() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::isHealthy).isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void acceptsTrueWithActualServerContentType() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andRespond(withSuccess("true", MediaType.parseMediaType("text/html;charset=utf-8")));
        assertThat(client.isHealthy()).isTrue();
        server.verify();
    }

    @Test
    void acceptsFalseWithActualServerContentType() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andRespond(withSuccess("false", MediaType.TEXT_HTML));
        assertThat(client.isHealthy()).isFalse();
        server.verify();
    }

    @Test
    void acceptsSurroundingWhitespace() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andRespond(withSuccess(" \ntrue\r\n", MediaType.TEXT_PLAIN));
        assertThat(client.isHealthy()).isTrue();
        server.verify();
    }

    @Test
    void rejectsHtmlErrorPageInsteadOfReturningFalse() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andRespond(withSuccess("<html>error</html>", MediaType.TEXT_HTML));
        assertThatThrownBy(client::isHealthy).isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void rejectsWhitespaceOnlyResponse() {
        server.expect(requestTo("https://sto.ssafy.io/health"))
                .andRespond(withSuccess(" \n", MediaType.TEXT_PLAIN));
        assertThatThrownBy(client::isHealthy).isInstanceOf(RestClientException.class);
        server.verify();
    }
}
