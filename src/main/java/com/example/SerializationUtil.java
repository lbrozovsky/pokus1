package com.example;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
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

    /** First byte of every serialized file — identifies the compression codec used. */
    private static final int MAGIC_PLAIN = 0x00;
    private static final int MAGIC_GZIP  = 0x01;
    private static final int MAGIC_XZ    = 0x02;

    /** Supported compression codecs. */
    public enum Compression {
        NONE, GZIP, XZ
    }

    private SerializationUtil() {}

    /** Serializes {@code obj} to {@code path} without compression. */
    public static void serialize(Object obj, Path path) throws IOException {
        serialize(obj, path, Compression.NONE);
    }

    /**
     * Serializes {@code obj} to {@code path}.
     *
     * <p>When {@code compress} is {@code true}, all available compression codecs
     * (GZIP, XZ) are tried in memory and the one producing the smallest output is used.
     * The chosen codec is recorded in the magic byte so that {@link #deserialize(Path)}
     * can detect it automatically.
     *
     * <p>When {@code compress} is {@code false}, no compression is applied.
     */
    public static void serialize(Object obj, Path path, boolean compress) throws IOException {
        if (!compress) {
            serialize(obj, path, Compression.NONE);
            return;
        }
        byte[] raw     = toBytes(obj);
        byte[] gzipped = compress(raw, Compression.GZIP);
        byte[] xzed    = compress(raw, Compression.XZ);
        if (gzipped.length <= xzed.length) {
            writeToFile(path, MAGIC_GZIP, gzipped);
        } else {
            writeToFile(path, MAGIC_XZ, xzed);
        }
    }

    /**
     * Serializes {@code obj} to {@code path} using the specified compression {@code codec}.
     * The codec is recorded in the magic byte so that {@link #deserialize(Path)}
     * can detect it automatically.
     */
    public static void serialize(Object obj, Path path, Compression codec) throws IOException {
        byte[] raw = toBytes(obj);
        if (codec == Compression.NONE) {
            writeToFile(path, MAGIC_PLAIN, raw);
        } else {
            byte[] compressed = compress(raw, codec);
            int magic = (codec == Compression.GZIP) ? MAGIC_GZIP : MAGIC_XZ;
            writeToFile(path, magic, compressed);
        }
    }

    /**
     * Deserializes an object from the given path.
     *
     * <p>The compression format is detected automatically from the magic byte written
     * by any of the {@code serialize} overloads (0x00 = none, 0x01 = GZIP, 0x02 = XZ).
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
            if (magic == MAGIC_PLAIN) {
                try (ObjectInputStream ois = new ObjectInputStream(buf)) {
                    return (T) ois.readObject();
                }
            } else if (magic == MAGIC_GZIP) {
                try (GZIPInputStream gzip = new GZIPInputStream(buf);
                     ObjectInputStream ois = new ObjectInputStream(gzip)) {
                    return (T) ois.readObject();
                }
            } else if (magic == MAGIC_XZ) {
                try (XZInputStream xz = new XZInputStream(buf);
                     ObjectInputStream ois = new ObjectInputStream(xz)) {
                    return (T) ois.readObject();
                }
            } else {
                throw new IOException("Unknown format byte 0x" + Integer.toHexString(magic) + " in: " + path);
            }
        }
    }

    private static byte[] toBytes(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static byte[] compress(byte[] data, Compression codec) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        switch (codec) {
            case GZIP -> {
                try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                    gzip.write(data);
                }
            }
            case XZ -> {
                try (XZOutputStream xz = new XZOutputStream(baos, new LZMA2Options())) {
                    xz.write(data);
                }
            }
            default -> throw new IllegalArgumentException("Cannot compress with codec: " + codec);
        }
        return baos.toByteArray();
    }

    private static void writeToFile(Path path, int magic, byte[] data) throws IOException {
        try (OutputStream file = Files.newOutputStream(path);
             BufferedOutputStream buf = new BufferedOutputStream(file)) {
            buf.write(magic);
            buf.write(data);
        }
    }
}
