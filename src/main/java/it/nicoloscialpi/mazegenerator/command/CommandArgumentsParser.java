package it.nicoloscialpi.mazegenerator.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

public class CommandArgumentsParser {

    private final HashMap<String, String> argumentValue;

    public CommandArgumentsParser(ArrayList<String> arguments) {
        argumentValue = new HashMap<>();
        for (String argument : arguments) {
            String[] split = argument.split(":");
            if (split.length != 2) {
                continue;
            }
            argumentValue.put(split[0].toLowerCase(), split[1].toLowerCase());
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CommandArgumentsParser{");
        if (argumentValue.isEmpty()) {
            sb.append("No arguments}");
        } else {
            argumentValue.forEach((key, value) -> sb.append(key).append(": ").append(value).append(", "));
            sb.delete(sb.length() - 2, sb.length()); // Removes the last comma and space
            sb.append("}");
        }
        return sb.toString();
    }
}
