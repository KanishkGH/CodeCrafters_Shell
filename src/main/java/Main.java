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

            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            String[] inputArgs = input.split("\\s+");
            String command = inputArgs[0];

            if (command.equals("exit")) {
                System.exit(0);
            }
            else if (command.equals("echo")) {
                String[] echoArgs = Arrays.copyOfRange(inputArgs, 1, inputArgs.length);
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
                System.out.println(currentDirectory.getAbsolutePath());
            }
            else if (command.equals("cd")) {
                if (inputArgs.length < 2) {
                    continue;
                }

                File newDir = new File(inputArgs[1]);

                if (newDir.exists() && newDir.isDirectory()) {
                    currentDirectory = newDir.getAbsoluteFile();
                } else {
                    System.out.println("cd: " + inputArgs[1] + ": No such file or directory");
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
                        System.out.println("Error executing command: " + e.getMessage());
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
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