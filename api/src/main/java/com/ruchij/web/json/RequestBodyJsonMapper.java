package com.ruchij.web.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruchij.exceptions.InvalidRequestBodyException;
import io.javalin.json.JavalinJackson;
import io.javalin.json.JsonMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.stream.Stream;

/**
 * Javalin's {@link JsonMapper}, backed by {@link JavalinJackson}, that turns a request body Jackson cannot read into a
 * {@link InvalidRequestBodyException} (400) instead of letting the raw Jackson exception reach the catch-all 500 handler.
 *
 * <p>Javalin only calls the {@code fromJson*} methods to read request bodies, so this is the one place a parse failure
 * is known to be the caller's fault. Jackson exceptions raised anywhere else (for example while reading a downstream
 * service's response) are still server errors. The Jackson message is deliberately not echoed: it can name internal
 * classes.
 */
public final class RequestBodyJsonMapper implements JsonMapper {
    private final JavalinJackson delegate;

    public RequestBodyJsonMapper(ObjectMapper objectMapper) {
        this.delegate = new JavalinJackson(objectMapper, true);
    }

    @Override
    public <T> T fromJsonString(String json, Type targetType) {
        try {
            return delegate.fromJsonString(json, targetType);
        } catch (Exception exception) {
            throw translate(exception);
        }
    }

    @Override
    public <T> T fromJsonStream(InputStream json, Type targetType) {
        try {
            return delegate.fromJsonStream(json, targetType);
        } catch (Exception exception) {
            throw translate(exception);
        }
    }

    @Override
    public String toJsonString(Object obj, Type type) {
        return delegate.toJsonString(obj, type);
    }

    @Override
    public InputStream toJsonStream(Object obj, Type type) {
        return delegate.toJsonStream(obj, type);
    }

    @Override
    public void writeToOutputStream(Stream<?> stream, OutputStream outputStream) {
        delegate.writeToOutputStream(stream, outputStream);
    }

    // JavalinJackson is written in Kotlin, so checked Jackson exceptions surface here undeclared.
    private static RuntimeException translate(Exception exception) {
        if (exception instanceof JacksonException) {
            return new InvalidRequestBodyException(exception);
        }

        return exception instanceof RuntimeException runtimeException
                ? runtimeException
                : new IllegalStateException(exception);
    }
}
