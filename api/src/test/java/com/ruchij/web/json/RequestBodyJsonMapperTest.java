package com.ruchij.web.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ruchij.exceptions.InvalidRequestBodyException;
import com.ruchij.utils.JsonUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RequestBodyJsonMapperTest {
    private final RequestBodyJsonMapper jsonMapper = new RequestBodyJsonMapper(JsonUtils.OBJECT_MAPPER);

    @Test
    void shouldReadValidJsonFromStringAndStream() {
        Map<String, Integer> fromString = jsonMapper.fromJsonString("{\"a\":1}", Map.class);
        Map<String, Integer> fromStream = jsonMapper.fromJsonStream(
                new ByteArrayInputStream("{\"a\":1}".getBytes(StandardCharsets.UTF_8)), Map.class);

        assertEquals(Map.of("a", 1), fromString);
        assertEquals(Map.of("a", 1), fromStream);
    }

    @Test
    void shouldTranslateUnreadableJsonToInvalidRequestBody() {
        InvalidRequestBodyException fromString =
                assertThrows(InvalidRequestBodyException.class, () -> jsonMapper.fromJsonString("{", Map.class));
        InvalidRequestBodyException fromStream = assertThrows(
                InvalidRequestBodyException.class,
                () -> jsonMapper.fromJsonStream(new ByteArrayInputStream(new byte[] {'['}), Map.class));

        assertEquals(InvalidRequestBodyException.MESSAGE, fromString.getMessage());
        assertEquals(InvalidRequestBodyException.MESSAGE, fromStream.getMessage());
    }

    @Test
    void shouldRethrowRuntimeExceptionsUnchanged() {
        UncheckedIOException failure = new UncheckedIOException(new IOException("socket closed"));

        RuntimeException thrown = assertThrows(
                RuntimeException.class, () -> jsonMapper.fromJsonStream(failingStream(failure), Map.class));

        assertSame(failure, thrown);
    }

    @Test
    void shouldWrapCheckedNonJacksonExceptions() {
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> jsonMapper.fromJsonStream(failingStream(new IOException("socket closed")), Map.class));

        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void shouldDelegateWrites() throws IOException {
        assertEquals("{\"a\":1}", jsonMapper.toJsonString(Map.of("a", 1), Map.class));

        try (InputStream inputStream = jsonMapper.toJsonStream(Map.of("a", 1), Map.class)) {
            assertEquals("{\"a\":1}", new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        jsonMapper.writeToOutputStream(Stream.of(1, 2), outputStream);
        assertEquals("[1,2]", outputStream.toString(StandardCharsets.UTF_8));
    }

    private static InputStream failingStream(Throwable failure) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return throwFailure();
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                return throwFailure();
            }

            private int throwFailure() throws IOException {
                if (failure instanceof IOException ioException) {
                    throw ioException;
                }
                throw (RuntimeException) failure;
            }
        };
    }
}
