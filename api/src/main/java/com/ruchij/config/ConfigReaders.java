package com.ruchij.config;

import com.typesafe.config.ConfigException;

import java.util.Optional;
import java.util.function.Supplier;

public class ConfigReaders {
    public static <T> Optional<T> optionalConfig(Supplier<T> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (ConfigException.Missing missingException) {
            return Optional.empty();
        }
    }
}
