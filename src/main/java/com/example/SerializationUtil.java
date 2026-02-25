package com.example;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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

    private static final byte MAGIC_PLAIN = 0x00;
    private static final byte MAGIC_GZIP  = 0x01;

    private SerializationUtil() {}

    public static void serialize(Object obj, Path path) throws IOException {
        serialize(obj, path, false);
    }

    public static void serialize(Object obj, Path path, boolean compress) throws IOException {
        try (OutputStream file = Files.newOutputStream(path);
             BufferedOutputStream buf = new BufferedOutputStream(file)) {
            buf.write(compress ? MAGIC_GZIP : MAGIC_PLAIN);
            if (compress) {
                try (GZIPOutputStream gzip = new GZIPOutputStream(buf);
                     ObjectOutputStream oos = new ObjectOutputStream(gzip)) {
                    oos.writeObject(obj);
                }
            } else {
                try (ObjectOutputStream oos = new ObjectOutputStream(buf)) {
                    oos.writeObject(obj);
                }
            }
        }
    }

    /**
     * Deserializes an object from the given path.
     *
     * <p>The compression format is detected automatically from the first byte written
     * by {@link #serialize(Object, Path, boolean)}.
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
        try (InputStream file = Files.newInputStream(path);
             BufferedInputStream buf = new BufferedInputStream(file)) {
            int magic = buf.read();
            if (magic == -1) {
                throw new IOException("File is empty: " + path);
            }
            if (magic == MAGIC_GZIP) {
                try (GZIPInputStream gzip = new GZIPInputStream(buf);
                     ObjectInputStream ois = new ObjectInputStream(gzip)) {
                    return (T) ois.readObject();
                }
            } else if (magic == MAGIC_PLAIN) {
                try (ObjectInputStream ois = new ObjectInputStream(buf)) {
                    return (T) ois.readObject();
                }
            } else {
                throw new IOException("Unknown format byte 0x" + Integer.toHexString(magic) + " in: " + path);
            }
        }
    }
}
