import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        Set<String> builtins = Set.of("exit", "echo", "type");

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            // Split the input into command and arguments
            String[] inputArgs = input.split("\\s+");
            String command = inputArgs[0];

            // 1. Handle "exit"
            if (command.equals("exit")) {
                System.exit(0);
            } 
            // 2. Handle "echo"
            else if (command.equals("echo")) {
                String[] echoArgs = Arrays.copyOfRange(inputArgs, 1, inputArgs.length);
                System.out.println(String.join(" ", echoArgs));
            } 
            // 3. Handle "type"
            else if (command.equals("type")) {
                if (inputArgs.length < 2) continue;
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
            // 4. Handle External Programs
            else {
                String path = getPath(command);
                if (path != null) {
                    try {
                        // ProcessBuilder takes the entire array/list of arguments
                        ProcessBuilder pb = new ProcessBuilder(inputArgs);
                        
                        // This mirrors the sub-process stdout/stderr directly to your shell's console
                        pb.inheritIO(); 
                        
                        Process process = pb.start();
                        process.waitFor(); // Wait for the external program to finish
                    } catch (Exception e) {
                        System.out.println("Error executing command: " + e.getMessage());
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }

    // Helper method to look up a command in the system PATH
    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        // Paths are separated by ":" on Unix/macOS and ";" on Windows
        String delimiter = System.getProperty("os.name").toLowerCase().contains("win") ? ";" : ":";
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