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
        try (OutputStream file = Files.newOutputStream(path);
             OutputStream out = compress ? new GZIPOutputStream(file) : file;
             ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(obj);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Path path) throws IOException, ClassNotFoundException {
        return deserialize(path, false);
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Path path, boolean compress) throws IOException, ClassNotFoundException {
        try (InputStream file = Files.newInputStream(path);
             InputStream in = compress ? new GZIPInputStream(file) : file;
             ObjectInputStream ois = new ObjectInputStream(in)) {
            return (T) ois.readObject();
        }
    }
}
