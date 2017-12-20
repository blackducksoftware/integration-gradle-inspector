package com.blackducksoftware.integration.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.blackducksoftware.integration.util.ResourceUtil;

public class Version {
    public static void main(final String[] args) {
        try {
            final String version = ResourceUtil.getResourceAsString("version.txt", StandardCharsets.UTF_8);
            System.out.println(version);
        } catch (final IOException e) {
            System.out.println("unknown_error");
        }
    }
}
