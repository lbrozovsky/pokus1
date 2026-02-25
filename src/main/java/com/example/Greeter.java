package com.example;

public class Greeter {
    public String greet(String name) {
        validateName(name);
        return "Hello, " + name + '!';
    }

    public String farewell(String name) {
        validateName(name);
        return "Goodbye, " + name + '!';
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be null or blank");
        }
    }
}
