package com.xerocode.web;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.teavm.extension.spi.resources.ResourcesPolicy;

public class EmbeddedResources implements ResourcesPolicy {
    @Override
    public String[] supplyResources(Collection<? extends String> classNames) {
        List<String> out = new ArrayList<>();
        try (InputStream in = EmbeddedResources.class.getClassLoader().getResourceAsStream("web/resources.list")) {
            if (in == null) return new String[0];
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) if (!line.isBlank()) out.add(line.trim());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out.toArray(new String[0]);
    }
}
