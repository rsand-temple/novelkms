package com.richardsand.novelkms;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.hubspot.jinjava.Jinjava;

import io.dropwizard.configuration.ConfigurationSourceProvider;

public class Jinja2ConfigurationSourceProvider implements ConfigurationSourceProvider {

    private final ConfigurationSourceProvider delegate;
    private final Jinjava                     jinjava;

    public Jinja2ConfigurationSourceProvider(ConfigurationSourceProvider delegate) {
        this.delegate = delegate;
        this.jinjava = new Jinjava();
    }

    @Override
    public InputStream open(String path) throws IOException {
        // 1. Read the raw YAML template using the original provider
        try (InputStream in = delegate.open(path)) {
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            // 2. Prepare the context (passing Environment Variables to Jinja2)
            Map<String, Object> context = new HashMap<>(System.getenv());

            // 3. Render the template
            String renderedYaml = jinjava.render(template, context);

            // 4. Return the rendered string as a fresh InputStream for Dropwizard
            return new ByteArrayInputStream(renderedYaml.getBytes(StandardCharsets.UTF_8));
        }
    }
}
