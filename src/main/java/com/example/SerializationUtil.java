package com.example;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
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
    private static final int MAGIC_BZIP2 = 0x03;

    private static final Compression[] AUTO_CODECS = { Compression.GZIP, Compression.XZ, Compression.BZIP2 };

    /** Supported compression codecs. */
    public enum Compression { NONE, GZIP, XZ, BZIP2 }

    private SerializationUtil() {}

    /** Serializes {@code obj} to {@code path} without compression. */
    public static void serialize(Object obj, Path path) throws IOException {
        serialize(obj, path, Compression.NONE);
    }

    /**
     * Serializes {@code obj} to {@code path}.
     *
     * <p>When {@code compress} is {@code true}, the object is serialized once into a byte
     * array. Each codec is then measured by piping those bytes through a
     * {@link CountingOutputStream} — the compressed output is counted but never retained,
     * so no additional large buffers are allocated. The winning codec writes the final
     * file in a single streaming pass over the already-held raw bytes.
     *
     * <p>When {@code compress} is {@code false}, no compression is applied.
     */
    public static void serialize(Object obj, Path path, boolean compress) throws IOException {
        if (!compress) {
            serialize(obj, path, Compression.NONE);
            return;
        }
        byte[] raw = toBytes(obj);

        Compression best = AUTO_CODECS[0];
        long bestSize = countCompressed(raw, best);
        for (int i = 1; i < AUTO_CODECS.length; i++) {
            long size = countCompressed(raw, AUTO_CODECS[i]);
            if (size < bestSize) {
                best     = AUTO_CODECS[i];
                bestSize = size;
            }
        }

        writeCompressed(path, raw, best);
    }

    /**
     * Serializes {@code obj} to {@code path} using the specified compression {@code codec}.
     * Data is streamed directly to the file — the object is never fully buffered in memory.
     */
    public static void serialize(Object obj, Path path, Compression codec) throws IOException {
        try (OutputStream file = Files.newOutputStream(path);
             BufferedOutputStream buf = new BufferedOutputStream(file)) {
            buf.write(magicFor(codec));
            if (codec == Compression.NONE) {
                try (ObjectOutputStream oos = new ObjectOutputStream(buf)) {
                    oos.writeObject(obj);
                }
            } else {
                try (OutputStream compressed = openCompressor(buf, codec);
                     ObjectOutputStream oos = new ObjectOutputStream(compressed)) {
                    oos.writeObject(obj);
                }
            }
        }
    }

    /**
     * Deserializes an object from the given path.
     *
     * <p>The compression format is detected automatically from the magic byte:
     * 0x00 = none, 0x01 = GZIP, 0x02 = XZ, 0x03 = BZip2.
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
            } else if (magic == MAGIC_BZIP2) {
                try (BZip2CompressorInputStream bzip2 = new BZip2CompressorInputStream(buf);
                     ObjectInputStream ois = new ObjectInputStream(bzip2)) {
                    return (T) ois.readObject();
                }
            } else {
                throw new IOException("Unknown format byte 0x" + Integer.toHexString(magic) + " in: " + path);
            }
        }
    }

    // --- private helpers ---

    private static byte[] toBytes(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    /**
     * Compresses {@code data} through a {@link CountingOutputStream} and returns
     * the byte count. No compressed output is retained — only the size is measured.
     */
    private static long countCompressed(byte[] data, Compression codec) throws IOException {
        CountingOutputStream counter = new CountingOutputStream();
        try (OutputStream compressor = openCompressor(counter, codec)) {
            compressor.write(data);
        }
        return counter.getCount();
    }

    private static void writeCompressed(Path path, byte[] raw, Compression codec) throws IOException {
        try (OutputStream file = Files.newOutputStream(path);
             BufferedOutputStream buf = new BufferedOutputStream(file)) {
            buf.write(magicFor(codec));
            try (OutputStream compressed = openCompressor(buf, codec)) {
                compressed.write(raw);
            }
        }
    }

    private static OutputStream openCompressor(OutputStream out, Compression codec) throws IOException {
        return switch (codec) {
            case GZIP  -> new GZIPOutputStream(out);
            case XZ    -> new XZOutputStream(out, new LZMA2Options());
            case BZIP2 -> new BZip2CompressorOutputStream(out);
            case NONE  -> throw new IllegalArgumentException("Cannot use NONE codec for compression");
        };
    }

    private static int magicFor(Compression codec) {
        return switch (codec) {
            case NONE  -> MAGIC_PLAIN;
            case GZIP  -> MAGIC_GZIP;
            case XZ    -> MAGIC_XZ;
            case BZIP2 -> MAGIC_BZIP2;
        };
    }

    /** Counts bytes written to it; discards all data. */
    private static final class CountingOutputStream extends OutputStream {
        private long count;

        @Override public void write(int b)                      { count++; }
        @Override public void write(byte[] b, int off, int len) { count += len; }
        @Override public void write(byte[] b)                   { count += b.length; }

        long getCount() { return count; }
    }
}
