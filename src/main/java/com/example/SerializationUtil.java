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
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class SerializationUtil {

    /**
     * First byte of every serialized file — identifies the compression codec used.
     * Magic bytes 0x11–0x13 use bit 0x10 as a Huffman post-processing flag:
     * lower nibble = base codec (1=GZIP, 2=XZ, 3=BZIP2), upper bit = Huffman applied on top.
     */
    private static final int MAGIC_PLAIN         = 0x00;
    private static final int MAGIC_GZIP          = 0x01;
    private static final int MAGIC_XZ            = 0x02;
    private static final int MAGIC_BZIP2         = 0x03;
    private static final int MAGIC_HUFFMAN       = 0x04;  // standalone Huffman-only
    private static final int MAGIC_GZIP_HUFFMAN  = 0x11;  // GZIP then Huffman post-processing
    private static final int MAGIC_XZ_HUFFMAN    = 0x12;  // XZ   then Huffman post-processing
    private static final int MAGIC_BZIP2_HUFFMAN = 0x13;  // BZip2 then Huffman post-processing

    /** Supported compression codecs. */
    public enum Compression { NONE, GZIP, XZ, BZIP2, HUFFMAN }

    /**
     * Internal pairing of a primary codec with an optional Huffman post-processing step.
     * The Huffman layer is applied after (and thus wraps) the base codec in the file stream.
     */
    private record Format(Compression codec, boolean huffman) {}

    /**
     * All format combinations tried during auto-selection.
     * Includes both plain codecs and codec+Huffman variants; the smallest wins.
     */
    private static final Format[] AUTO_FORMATS = {
        new Format(Compression.GZIP,    false),
        new Format(Compression.XZ,      false),
        new Format(Compression.BZIP2,   false),
        new Format(Compression.HUFFMAN, false),
        new Format(Compression.GZIP,    true),
        new Format(Compression.XZ,      true),
        new Format(Compression.BZIP2,   true),
    };

    private SerializationUtil() {}

    /** Serializes {@code obj} to {@code path} without compression. */
    public static void serialize(Object obj, Path path) throws IOException {
        serialize(obj, path, Compression.NONE);
    }

    /**
     * Serializes {@code obj} to {@code path}.
     *
     * <p>When {@code compress} is {@code true}, the object is serialized once into a byte
     * array. Every codec+Huffman combination is then measured by piping those bytes through a
     * {@link CountingOutputStream} — the compressed output is counted but never retained,
     * so no additional large buffers are allocated. The winning format writes the final
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

        Format best = AUTO_FORMATS[0];
        long bestSize = countCompressed(raw, best);
        for (int i = 1; i < AUTO_FORMATS.length; i++) {
            long size = countCompressed(raw, AUTO_FORMATS[i]);
            if (size < bestSize) {
                best     = AUTO_FORMATS[i];
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
            buf.write(magicFor(new Format(codec, false)));
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
     * 0x00 = none, 0x01 = GZIP, 0x02 = XZ, 0x03 = BZip2, 0x04 = Huffman,
     * 0x11 = GZIP+Huffman, 0x12 = XZ+Huffman, 0x13 = BZip2+Huffman.
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
            return switch (magic) {
                case MAGIC_PLAIN -> {
                    try (ObjectInputStream ois = new ObjectInputStream(buf)) {
                        yield (T) ois.readObject();
                    }
                }
                case MAGIC_GZIP -> {
                    try (GZIPInputStream gzip = new GZIPInputStream(buf);
                         ObjectInputStream ois = new ObjectInputStream(gzip)) {
                        yield (T) ois.readObject();
                    }
                }
                case MAGIC_XZ -> {
                    try (XZInputStream xz = new XZInputStream(buf);
                         ObjectInputStream ois = new ObjectInputStream(xz)) {
                        yield (T) ois.readObject();
                    }
                }
                case MAGIC_BZIP2 -> {
                    try (BZip2CompressorInputStream bzip2 = new BZip2CompressorInputStream(buf);
                         ObjectInputStream ois = new ObjectInputStream(bzip2)) {
                        yield (T) ois.readObject();
                    }
                }
                case MAGIC_HUFFMAN -> {
                    try (InputStream huffman = openHuffmanDecompressor(buf);
                         ObjectInputStream ois = new ObjectInputStream(huffman)) {
                        yield (T) ois.readObject();
                    }
                }
                case MAGIC_GZIP_HUFFMAN -> {
                    try (InputStream huffman = openHuffmanDecompressor(buf);
                         GZIPInputStream gzip = new GZIPInputStream(huffman);
                         ObjectInputStream ois = new ObjectInputStream(gzip)) {
                        yield (T) ois.readObject();
                    }
                }
                case MAGIC_XZ_HUFFMAN -> {
                    try (InputStream huffman = openHuffmanDecompressor(buf);
                         XZInputStream xz = new XZInputStream(huffman);
                         ObjectInputStream ois = new ObjectInputStream(xz)) {
                        yield (T) ois.readObject();
                    }
                }
                case MAGIC_BZIP2_HUFFMAN -> {
                    try (InputStream huffman = openHuffmanDecompressor(buf);
                         BZip2CompressorInputStream bzip2 = new BZip2CompressorInputStream(huffman);
                         ObjectInputStream ois = new ObjectInputStream(bzip2)) {
                        yield (T) ois.readObject();
                    }
                }
                default -> throw new IOException(
                        "Unknown format byte 0x" + Integer.toHexString(magic) + " in: " + path);
            };
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
    private static long countCompressed(byte[] data, Format fmt) throws IOException {
        CountingOutputStream counter = new CountingOutputStream();
        try (OutputStream compressor = openFormatCompressor(counter, fmt)) {
            compressor.write(data);
        }
        return counter.getCount();
    }

    private static void writeCompressed(Path path, byte[] raw, Format fmt) throws IOException {
        try (OutputStream file = Files.newOutputStream(path);
             BufferedOutputStream buf = new BufferedOutputStream(file)) {
            buf.write(magicFor(fmt));
            try (OutputStream compressor = openFormatCompressor(buf, fmt)) {
                compressor.write(raw);
            }
        }
    }

    /**
     * Opens an output stream for the given format.
     *
     * <p>When {@code fmt.huffman()} is {@code true}, the Huffman compressor is the inner
     * (innermost-to-file) layer and the base codec is the outer layer. Data flows:
     * raw → base-codec → Huffman → file.  On the read side the order is reversed:
     * file → Huffman-decompress → base-codec-decompress → raw.
     */
    private static OutputStream openFormatCompressor(OutputStream out, Format fmt)
            throws IOException {
        if (fmt.huffman()) {
            OutputStream huffmanOut = openHuffmanCompressor(out);
            return openCompressor(huffmanOut, fmt.codec());
        }
        return openCompressor(out, fmt.codec());
    }

    private static OutputStream openCompressor(OutputStream out, Compression codec)
            throws IOException {
        return switch (codec) {
            case GZIP    -> new GZIPOutputStream(out);
            case XZ      -> new XZOutputStream(out, new LZMA2Options());
            case BZIP2   -> new BZip2CompressorOutputStream(out);
            case HUFFMAN -> openHuffmanCompressor(out);
            case NONE    -> throw new IllegalArgumentException(
                    "Cannot use NONE codec for compression");
        };
    }

    /**
     * Opens a raw-DEFLATE compressor using the Huffman-only strategy.
     * The returned stream calls {@link Deflater#end()} when closed.
     */
    private static OutputStream openHuffmanCompressor(OutputStream out) {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        def.setStrategy(Deflater.HUFFMAN_ONLY);
        return new DeflaterOutputStream(out, def) {
            @Override public void close() throws IOException {
                try { super.close(); } finally { def.end(); }
            }
        };
    }

    /**
     * Opens a raw-DEFLATE decompressor matching {@link #openHuffmanCompressor}.
     * The returned stream calls {@link Inflater#end()} when closed.
     */
    private static InputStream openHuffmanDecompressor(InputStream in) {
        Inflater inf = new Inflater(true);
        return new InflaterInputStream(in, inf) {
            @Override public void close() throws IOException {
                try { super.close(); } finally { inf.end(); }
            }
        };
    }

    private static int magicFor(Format fmt) {
        if (fmt.huffman()) {
            return switch (fmt.codec()) {
                case GZIP  -> MAGIC_GZIP_HUFFMAN;
                case XZ    -> MAGIC_XZ_HUFFMAN;
                case BZIP2 -> MAGIC_BZIP2_HUFFMAN;
                default    -> throw new IllegalArgumentException(
                        "Unsupported codec for CODEC+Huffman combination: " + fmt.codec());
            };
        }
        return switch (fmt.codec()) {
            case NONE    -> MAGIC_PLAIN;
            case GZIP    -> MAGIC_GZIP;
            case XZ      -> MAGIC_XZ;
            case BZIP2   -> MAGIC_BZIP2;
            case HUFFMAN -> MAGIC_HUFFMAN;
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
