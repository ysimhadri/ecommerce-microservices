package com.ecommerce.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

final class RemoteResponses {

    private RemoteResponses() {
    }

    static String errorCode(ClientHttpResponse response, ObjectMapper objectMapper) {
        try {
            byte[] body = response.getBody().readAllBytes();
            if (body.length == 0) {
                return "";
            }
            JsonNode node = objectMapper.readTree(body);
            JsonNode code = node.get("code");
            return code == null || code.isNull() ? "" : code.asText("");
        } catch (IOException ex) {
            return "";
        }
    }
}
