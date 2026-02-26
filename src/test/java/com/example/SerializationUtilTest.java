package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SerializationUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void serializeAndDeserializeString() throws Exception {
        Path file = tempDir.resolve("test.ser");
        SerializationUtil.serialize("hello world", file);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void serializeAndDeserializeWithCompression() throws Exception {
        Path file = tempDir.resolve("test.ser.gz");
        SerializationUtil.serialize("hello world", file, true);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void autoDetectsBothFormatsFromSameDeserializeMethod() throws Exception {
        Path plain = tempDir.resolve("plain.ser");
        Path compressed = tempDir.resolve("compressed.ser.gz");

        SerializationUtil.serialize("plain", plain, false);
        SerializationUtil.serialize("compressed", compressed, true);

        assertEquals("plain", SerializationUtil.deserialize(plain));
        assertEquals("compressed", SerializationUtil.deserialize(compressed));
    }

    @Test
    void compressedFileIsSmallerForLargeData() throws Exception {
        // byte array of zeros is highly compressible and avoids Java serialization deduplication
        byte[] data = new byte[100_000];
        Path plain = tempDir.resolve("plain.ser");
        Path compressed = tempDir.resolve("compressed.ser.gz");

        SerializationUtil.serialize(data, plain);
        SerializationUtil.serialize(data, compressed, true);

        assertTrue(compressed.toFile().length() < plain.toFile().length());
    }

    @Test
    void serializeAndDeserializeComplexObject() throws Exception {
        Path file = tempDir.resolve("list.ser");
        List<Integer> original = List.of(1, 2, 3, 4, 5);
        SerializationUtil.serialize(original, file);
        List<Integer> result = SerializationUtil.deserialize(file);
        assertEquals(original, result);
    }

    @Test
    void deserializeEmptyFileThrows() throws Exception {
        Path file = tempDir.resolve("empty.ser");
        Files.createFile(file);
        assertThrows(IOException.class, () -> SerializationUtil.deserialize(file));
    }

    @Test
    void deserializeUnknownMagicByteThrows() throws Exception {
        Path file = tempDir.resolve("unknown.ser");
        Files.write(file, new byte[]{0x42});
        assertThrows(IOException.class, () -> SerializationUtil.deserialize(file));
    }

    @Test
    void serializeNonSerializableObjectThrows() {
        Path file = tempDir.resolve("bad.ser");
        Object notSerializable = new Object() {};
        assertThrows(NotSerializableException.class,
                () -> SerializationUtil.serialize(notSerializable, file));
    }

    @Test
    void deserializeNonExistentFileThrows() {
        Path file = tempDir.resolve("missing.ser");
        assertThrows(IOException.class, () -> SerializationUtil.deserialize(file));
    }

    @Test
    void wrongTypeThrowsClassCastException() throws Exception {
        Path file = tempDir.resolve("int.ser");
        SerializationUtil.serialize(42, file);
        assertThrows(ClassCastException.class, () -> {
            String result = SerializationUtil.deserialize(file);
            result.length(); // trigger cast
        });
    }

    @Test
    void serializeAndDeserializeWithExplicitGzipCodec() throws Exception {
        Path file = tempDir.resolve("test.ser.gz");
        SerializationUtil.serialize("hello world", file, SerializationUtil.Compression.GZIP);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void serializeAndDeserializeWithExplicitXzCodec() throws Exception {
        Path file = tempDir.resolve("test.ser.xz");
        SerializationUtil.serialize("hello world", file, SerializationUtil.Compression.XZ);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void serializeAndDeserializeWithExplicitBzip2Codec() throws Exception {
        Path file = tempDir.resolve("test.ser.bz2");
        SerializationUtil.serialize("hello world", file, SerializationUtil.Compression.BZIP2);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void serializeAndDeserializeWithHuffmanCodec() throws Exception {
        Path file = tempDir.resolve("test.ser.huff");
        SerializationUtil.serialize("hello world", file, SerializationUtil.Compression.HUFFMAN);
        String result = SerializationUtil.deserialize(file);
        assertEquals("hello world", result);
    }

    @Test
    void huffmanCodecWritesCorrectMagicByte() throws Exception {
        Path file = tempDir.resolve("huffman.ser");
        SerializationUtil.serialize("data", file, SerializationUtil.Compression.HUFFMAN);
        int magic = Files.readAllBytes(file)[0] & 0xFF;
        assertEquals(0x04, magic, "Expected Huffman magic byte 0x04");
    }

    @Test
    void allCodecsRoundTripComplexObject() throws Exception {
        java.util.List<Integer> original = java.util.List.of(1, 2, 3, 4, 5);
        for (SerializationUtil.Compression codec : SerializationUtil.Compression.values()) {
            Path file = tempDir.resolve("list-" + codec + ".ser");
            SerializationUtil.serialize(original, file, codec);
            java.util.List<Integer> result = SerializationUtil.deserialize(file);
            assertEquals(original, result, "Round-trip failed for codec: " + codec);
        }
    }

    @Test
    void bzip2CompressedFileIsSmallerForLargeData() throws Exception {
        byte[] data = new byte[100_000];
        Path plain = tempDir.resolve("plain.ser");
        Path bzip2 = tempDir.resolve("compressed.ser.bz2");

        SerializationUtil.serialize(data, plain);
        SerializationUtil.serialize(data, bzip2, SerializationUtil.Compression.BZIP2);

        assertTrue(Files.size(bzip2) < Files.size(plain));
    }

    @Test
    void xzCompressedFileIsSmallerForLargeData() throws Exception {
        byte[] data = new byte[100_000];
        Path plain = tempDir.resolve("plain.ser");
        Path xz    = tempDir.resolve("compressed.ser.xz");

        SerializationUtil.serialize(data, plain);
        SerializationUtil.serialize(data, xz, SerializationUtil.Compression.XZ);

        assertTrue(Files.size(xz) < Files.size(plain));
    }

    @Test
    void autoCompressionWritesCompressedMagicByte() throws Exception {
        byte[] data = new byte[100_000];
        Path file = tempDir.resolve("auto.ser");
        SerializationUtil.serialize(data, file, true);

        int magic = Files.readAllBytes(file)[0] & 0xFF;
        // Valid magic bytes: plain codecs (0x01–0x04) or codec+Huffman variants (0x11–0x13)
        assertTrue(magic == 0x01 || magic == 0x02 || magic == 0x03 || magic == 0x04
                        || magic == 0x11 || magic == 0x12 || magic == 0x13,
                "Expected a compressed magic byte, got: 0x" + Integer.toHexString(magic));
    }

    @Test
    void autoCompressionPicksSmallestCodec() throws Exception {
        // 100K zeros are highly compressible — auto should match or beat every individual codec
        byte[] data = new byte[100_000];
        Path auto   = tempDir.resolve("auto.ser");
        Path gzip   = tempDir.resolve("gzip.ser");
        Path xz     = tempDir.resolve("xz.ser");
        Path bzip2  = tempDir.resolve("bzip2.ser");
        Path huffman = tempDir.resolve("huffman.ser");

        SerializationUtil.serialize(data, auto,    true);
        SerializationUtil.serialize(data, gzip,    SerializationUtil.Compression.GZIP);
        SerializationUtil.serialize(data, xz,      SerializationUtil.Compression.XZ);
        SerializationUtil.serialize(data, bzip2,   SerializationUtil.Compression.BZIP2);
        SerializationUtil.serialize(data, huffman, SerializationUtil.Compression.HUFFMAN);

        long autoSize = Files.size(auto);
        // Auto selects from all 7 combinations, so its result must be <= every individual codec
        assertTrue(autoSize <= Files.size(gzip),   "Auto should be no larger than GZIP");
        assertTrue(autoSize <= Files.size(xz),     "Auto should be no larger than XZ");
        assertTrue(autoSize <= Files.size(bzip2),  "Auto should be no larger than BZip2");
        assertTrue(autoSize <= Files.size(huffman), "Auto should be no larger than Huffman");

        // Must also deserialize correctly
        assertArrayEquals(data, (byte[]) SerializationUtil.deserialize(auto));
    }

    @Test
    void codecPlusHuffmanCombinationsRoundTrip() throws Exception {
        // Explicitly exercises the 0x11 (GZIP+Huffman), 0x12 (XZ+Huffman), 0x13 (BZip2+Huffman)
        // deserialization branches by manually writing files in those formats.
        String payload = "codec+huffman round-trip";

        int[] magics = {0x11, 0x12, 0x13};
        for (int magic : magics) {
            Path file = tempDir.resolve("combo_" + magic + ".ser");

            // Serialize payload to raw bytes
            ByteArrayOutputStream rawBuf = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(rawBuf)) {
                oos.writeObject(payload);
            }
            byte[] raw = rawBuf.toByteArray();

            // Write: magic | Huffman( BaseCodec( raw ) )
            try (OutputStream out = Files.newOutputStream(file)) {
                out.write(magic);
                Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                def.setStrategy(Deflater.HUFFMAN_ONLY);
                try (DeflaterOutputStream huffman = new DeflaterOutputStream(out, def) {
                    @Override public void close() throws IOException {
                        try { super.close(); } finally { def.end(); }
                    }
                }) {
                    try (OutputStream baseCodec = openBaseCodec(magic, huffman)) {
                        baseCodec.write(raw);
                    }
                }
            }

            assertEquals(0xFF & Files.readAllBytes(file)[0], magic,
                    "Wrong magic byte for combo 0x" + Integer.toHexString(magic));
            assertEquals(payload, SerializationUtil.deserialize(file),
                    "Deserialization failed for combo 0x" + Integer.toHexString(magic));
        }
    }

    private static OutputStream openBaseCodec(int magic, OutputStream out) throws IOException {
        return switch (magic) {
            case 0x11 -> new GZIPOutputStream(out);
            case 0x12 -> new XZOutputStream(out, new LZMA2Options());
            case 0x13 -> new BZip2CompressorOutputStream(out);
            default   -> throw new IllegalArgumentException("Unknown combo magic: 0x" + Integer.toHexString(magic));
        };
    }
}
