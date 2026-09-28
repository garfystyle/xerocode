package net.minecraft.server.packs.resources;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.Identifier;

public final class ResourceManager {
    public InputStream open(Identifier id) throws IOException {
        InputStream in = ResourceManager.class.getResourceAsStream("/web/mc/" + id.getNamespace() + "/" + id.getPath());
        if (in == null) throw new FileNotFoundException(id.toString());
        return in;
    }

    public BufferedReader openAsReader(Identifier id) throws IOException {
        return new BufferedReader(new InputStreamReader(open(id), StandardCharsets.UTF_8));
    }
}
