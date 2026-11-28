package com.ruchij.config;

import com.typesafe.config.Config;

/**
 * The internal management server, which serves operational endpoints such as {@code /metrics} on a port that is never
 * exposed through the public Service/Ingress.
 */
public record ManagementConfiguration(int port) {
    public static ManagementConfiguration parse(Config config) {
        return new ManagementConfiguration(config.getInt("port"));
    }
}
