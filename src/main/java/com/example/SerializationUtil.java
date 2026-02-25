package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SerializationUtil {

    private SerializationUtil() {}

    public static void serialize(Object obj, Path path) throws IOException {
        serialize(obj, path, false);
    }

    public static void serialize(Object obj, Path path, boolean compress) throws IOException {
        try (OutputStream file = Files.newOutputStream(path)) {
            if (compress) {
                try (GZIPOutputStream gzip = new GZIPOutputStream(file);
                     ObjectOutputStream oos = new ObjectOutputStream(gzip)) {
                    oos.writeObject(obj);
                }
            } else {
                try (ObjectOutputStream oos = new ObjectOutputStream(file)) {
                    oos.writeObject(obj);
                }
            }
        }
    }

    /**
     * Deserializes an object from the given path.
     *
     * <p><strong>Security warning:</strong> Java native deserialization is a known
     * remote-code-execution vector. Only deserialize data from fully trusted sources.
     * Consider using {@link ObjectInputStream#setObjectInputFilter} or switching to
     * a safer format (JSON, Protobuf) for untrusted data.
     *
     * <p>The returned type is unchecked — a type mismatch throws {@link ClassCastException}
     * at the call site, not inside this method.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Path path) throws IOException, ClassNotFoundException {
        return deserialize(path, false);
    }

    /**
     * Deserializes an object from the given path, optionally decompressing with GZIP.
     *
     * @see #deserialize(Path)
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Path path, boolean compress) throws IOException, ClassNotFoundException {
        try (InputStream file = Files.newInputStream(path)) {
            if (compress) {
                try (GZIPInputStream gzip = new GZIPInputStream(file);
                     ObjectInputStream ois = new ObjectInputStream(gzip)) {
                    return (T) ois.readObject();
                }
            } else {
                try (ObjectInputStream ois = new ObjectInputStream(file)) {
                    return (T) ois.readObject();
                }
            }
        }
    }
}
