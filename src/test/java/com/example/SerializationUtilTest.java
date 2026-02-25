package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.NotSerializableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}
