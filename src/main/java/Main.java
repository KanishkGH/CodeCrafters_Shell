import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        Set<String> builtins = Set.of("exit", "echo", "type", "pwd", "cd");

        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine();

            if (input.trim().isEmpty()) {
                continue;
            }

            List<String> tokens = parseInput(input);
            String[] inputArgs = tokens.toArray(new String[0]);

            if (inputArgs.length == 0) {
                continue;
            }

            String command = inputArgs[0];

            if (command.equals("exit")) {
                System.exit(0);
            }
            else if (command.equals("echo")) {
                String[] echoArgs =
                        Arrays.copyOfRange(inputArgs, 1, inputArgs.length);
                System.out.println(String.join(" ", echoArgs));
            }
            else if (command.equals("type")) {
                if (inputArgs.length < 2) {
                    continue;
                }

                String targetCmd = inputArgs[1];

                if (builtins.contains(targetCmd)) {
                    System.out.println(targetCmd + " is a shell builtin");
                } else {
                    String path = getPath(targetCmd);

                    if (path != null) {
                        System.out.println(targetCmd + " is " + path);
                    } else {
                        System.out.println(targetCmd + ": not found");
                    }
                }
            }
            else if (command.equals("pwd")) {
                System.out.println(currentDirectory.getCanonicalPath());
            }
            else if (command.equals("cd")) {
                if (inputArgs.length < 2) {
                    continue;
                }

                File newDir;

                if (inputArgs[1].equals("~")) {
                    newDir = new File(System.getenv("HOME"));
                }
                else if (new File(inputArgs[1]).isAbsolute()) {
                    newDir = new File(inputArgs[1]);
                }
                else {
                    newDir = new File(currentDirectory, inputArgs[1]);
                }

                try {
                    newDir = newDir.getCanonicalFile();

                    if (newDir.exists() && newDir.isDirectory()) {
                        currentDirectory = newDir;
                    } else {
                        System.out.println(
                                "cd: " + inputArgs[1] + ": No such file or directory");
                    }
                } catch (IOException e) {
                    System.out.println(
                            "cd: " + inputArgs[1] + ": No such file or directory");
                }
            }
            else {
                String path = getPath(command);

                if (path != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(inputArgs);
                        pb.directory(currentDirectory);
                        pb.inheritIO();

                        Process process = pb.start();
                        process.waitFor();
                    } catch (Exception e) {
                        System.out.println(
                                "Error executing command: " + e.getMessage());
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }

    private static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'') {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inSingleQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String delimiter =
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? ";"
                        : ":";

        String[] directories = pathEnv.split(delimiter);

        for (String dir : directories) {
            File file = new File(dir, command);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }
}
