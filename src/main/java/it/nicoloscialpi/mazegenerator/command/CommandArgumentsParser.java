package it.nicoloscialpi.mazegenerator.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

public class CommandArgumentsParser {

    private final HashMap<String, String> argumentValue;

    public CommandArgumentsParser(ArrayList<String> arguments) {
        argumentValue = new HashMap<>();
        // Defensive copy to avoid modifying the passed list
        ArrayList<String> argsCopy = new ArrayList<>(arguments);
        for (String argument : argsCopy) {
            if (argument == null) continue;
            String[] split = argument.split(":", 2);
            if (split.length != 2) continue;
            String key = split[0].trim().toLowerCase();
            String value = split[1].trim();
            if (!key.isEmpty()) {
                argumentValue.put(key, value);
            }
        }
    }

    public Optional<Integer> getInt(String argName) {
        try {
            String string = argumentValue.get(argName.toLowerCase());
            if (string == null) {
                return Optional.empty();
            }
            return Optional.of(Integer.parseInt(string));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public Optional<Double> getDouble(String argName) {
        try {
            String string = argumentValue.get(argName.toLowerCase());
            if (string == null) {
                return Optional.empty();
            }
            return Optional.of(Double.parseDouble(string));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public Optional<String> getString(String argName) {
        try {
            String string = argumentValue.get(argName.toLowerCase());
            if (string == null) {
                return Optional.empty();
            }
            return Optional.of(string);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public Optional<Boolean> getBool(String argName) {
        try {
            String string = argumentValue.get(argName.toLowerCase());
            if (string == null) {
                return Optional.empty();
            }
            return Optional.of(Boolean.parseBoolean(string));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
