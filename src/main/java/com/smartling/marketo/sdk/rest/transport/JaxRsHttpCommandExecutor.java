package com.smartling.marketo.sdk.rest.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.smartling.marketo.sdk.HasToBeMappedToJson;
import com.smartling.marketo.sdk.rest.Command;
import com.smartling.marketo.sdk.MarketoApiException;
import com.smartling.marketo.sdk.rest.HttpCommandExecutor;
import com.smartling.marketo.sdk.rest.RequestLimitExceededException;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.JacksonFeature;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.*;
import java.util.Map.Entry;

public class JaxRsHttpCommandExecutor implements HttpCommandExecutor {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> API_LIMIT_ERROR_CODES = ImmutableSet.of(
            "606",  // Rate limit
            "607",  // Daily quota
            "615"   // Concurrent request limit
    );

    private final String identityUrl;
    private final String restUrl;
    private final String clientId;
    private final String clientSecret;
    private final TokenProvider tokenProvider;
    private final Client client;

    public JaxRsHttpCommandExecutor(String identityUrl, String restUrl, String clientId, String clientSecret, TokenProvider tokenProvider) {
        this.identityUrl = identityUrl;
        this.restUrl = restUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenProvider = tokenProvider;
        this.client = ClientBuilder.newClient().register(JacksonFeature.class).register(ObjectMapperProvider.class);
    }

    public void setConnectionTimeout(int timeout) {
        client.property(ClientProperties.CONNECT_TIMEOUT, timeout);
    }

    public void setSocketReadTimeout(int timeout) {
        client.property(ClientProperties.READ_TIMEOUT, timeout);
    }

    @Override
    public <T> T execute(final Command<T> command) throws MarketoApiException {
        ClientConnectionData clientConnectionData = new ClientConnectionData(client, identityUrl, clientId, clientSecret);
        String token = tokenProvider.authenticate(clientConnectionData).getAccessToken();

        WebTarget target = buildWebTarget(client, command);
        Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE).header("Authorization", "Bearer " + token);
        MarketoResponse<T> marketoResponse = execute(invocationBuilder, command);

        if (marketoResponse.isSuccess()) {
            return marketoResponse.getResult();
        } else {
            throw newApiException(command, marketoResponse.getErrors());
        }
    }

    private <T> MarketoResponse<T> execute(Invocation.Builder invocationBuilder, Command<T> command) throws MarketoApiException {
        GenericType<MarketoResponse<T>> typeToken = new GenericType<MarketoResponse<T>>(createReturnType(command)) {
        };

        MarketoResponse<T> marketoResponse;
        if ("POST".equalsIgnoreCase(command.getMethod())) {
            if (MediaType.APPLICATION_JSON_TYPE.equals(command.getMediaType())) {
                marketoResponse = invocationBuilder.post(Entity.json(command.getParameters()), typeToken);
            } else {
                Form form = toForm(processParameters(command.getParameters(), false));
                Entity<?> entity = Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE.withCharset("UTF-8"));
                marketoResponse = invocationBuilder.post(entity, typeToken);
            }
        } else {
            marketoResponse = invocationBuilder.method(command.getMethod(), typeToken);
        }

        return marketoResponse;
    }

    private static MarketoApiException newApiException(Command<?> command, List<MarketoResponse.Error> errors) {
        Optional<MarketoResponse.Error> requestLimitError = errors.stream()
                .filter(e -> API_LIMIT_ERROR_CODES.contains(e.getCode()))
                .findFirst();

        if (requestLimitError.isPresent()) {
            MarketoResponse.Error error = requestLimitError.get();
            return new RequestLimitExceededException(error.getCode(),
                    String.format("%s (%s:%s, parameters=%s)", error.getMessage(), command.getMethod(), command.getPath(),
                            command.getParameters()));
        } else {
            MarketoResponse.Error firstError = errors.get(0);
            return new MarketoApiException(firstError.getCode(),
                    String.format("%s (%s:%s, parameters=%s)", firstError.getMessage(), command.getMethod(), command.getPath(),
                            command.getParameters()));
        }
    }

    private Map<String, Object> processParameters(Map<String, Object> parameters, boolean needUrlEncode) throws MarketoApiException {

        try {
            Map<String, Object> processedParameters = new HashMap<>(parameters);
            for (Entry<String, Object> entry : processedParameters.entrySet()) {
                Object value = entry.getValue();
                Object newValue = value;
                if (value instanceof HasToBeMappedToJson) {
                    newValue = OBJECT_MAPPER.writeValueAsString(newValue);
                    if (needUrlEncode) {
                        newValue = URLEncoder.encode((String) newValue, "UTF-8");
                    }
                }
                entry.setValue(newValue);
            }
            return processedParameters;
        } catch (UnsupportedEncodingException | JsonProcessingException e) {
            throw new MarketoApiException(e.getMessage());
        }
    }

    private <T> WebTarget buildWebTarget(Client client, Command<T> command) throws MarketoApiException {
        WebTarget target = client.target(restUrl).path(command.getPath());
        if ("GET".equalsIgnoreCase(command.getMethod())) {
            for (Entry<String, Object> param : processParameters(command.getParameters(), true).entrySet()) {
                target = target.queryParam(param.getKey(), param.getValue());
            }
        }

        return target;
    }

    private static Form toForm(Map<String, Object> parameters) {
        Form form = new Form();
        for (Entry<String, Object> param : parameters.entrySet()) {
            form.param(param.getKey(), param.getValue().toString());
        }

        return form;
    }

    private static <T> ParameterizedType createReturnType(final Command<T> command) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[] { command.getResultType() };
            }

            @Override
            public Type getRawType() {
                return MarketoResponse.class;
            }

            @Override
            public Type getOwnerType() {
                return MarketoResponse.class;
            }
        };
    }
}
