import java.util.*;
import java.io.*;

public class Main {
    // Keep track of the shell's current working directory state
    private static String currentDir = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        // Updated builtins set to include 'cd' and 'pwd'
        Set<String> builtins = Set.of("exit", "echo", "type", "cd", "pwd");

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            // Using our updated backslash-aware command parser
            List<String> inputArgs = parseCommand(input);
            if (inputArgs.isEmpty()) {
                continue;
            }
            
            String command = inputArgs.get(0);

            // 1. Handle "exit"
            if (command.equals("exit")) {
                System.exit(0);
            } 
            // 2. Handle "echo"
            else if (command.equals("echo")) {
                List<String> echoArgs = inputArgs.subList(1, inputArgs.size());
                System.out.println(String.join(" ", echoArgs));
            } 
            // 3. Handle "pwd"
            else if (command.equals("pwd")) {
                System.out.println(currentDir);
            }
            // 4. Handle "cd"
            else if (command.equals("cd")) {
                String targetPath = inputArgs.size() > 1 ? inputArgs.get(1) : "~";
                
                if (targetPath.equals("~")) {
                    String homeDir = System.getenv("HOME");
                    if (homeDir != null) {
                        currentDir = homeDir;
                    }
                } else {
                    File file = new File(targetPath);
                    // Resolve relative paths based on our tracked directory
                    if (!file.isAbsolute()) {
                        file = new File(currentDir, targetPath);
                    }
                    
                    if (file.exists() && file.isDirectory()) {
                        // getCanonicalPath evaluates "." and ".." accurately
                        currentDir = file.getCanonicalPath();
                    } else {
                        System.out.println("cd: " + targetPath + ": No such file or directory");
                    }
                }
            }
            // 5. Handle "type"
            else if (command.equals("type")) {
                if (inputArgs.size() < 2) continue;
                String targetCmd = inputArgs.get(1);

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
            // 6. Handle External Programs
            else {
                String path = getPath(command);
                if (path != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(inputArgs);
                        
                        // Dynamically sets the environment context to our current directory state
                        pb.directory(new File(currentDir));
                        
                        // Inherit standard IO streams so the output prints to your shell
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

    // Upgraded quote and backslash-aware command parser
    private static List<String> parseCommand(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean isEscaped = false; // Tracks if the current character is being escaped by a backslash

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            // If the previous character was an unquoted backslash, 
            // treat this current character purely as a literal value.
            if (isEscaped) {
                current.append(ch);
                isEscaped = false; // Reset escaping state immediately
                continue;
            }

            // 1. If we are in double quotes, look only for the closing double quote
            if (inDoubleQuotes) {
                if (ch == '"') {
                    inDoubleQuotes = false; // Close double quotes
                } else {
                    current.append(ch);
                }
            }
            // 2. If we are in single quotes, look only for the closing single quote
            // 2. If we are in single quotes, look only for the closing single quote
else if (inSingleQuotes) {
    if (ch == '\'') {
        inSingleQuotes = false; // Close single quotes
    } else {
        current.append(ch); // Everything else is treated as a literal character!
    }
}
            // 3. If we are NOT inside any quotes
            else {
                if (ch == '\\') {
                    isEscaped = true; // Set escape flag; skips adding the backslash itself
                } else if (ch == '"') {
                    inDoubleQuotes = true; // Open double quotes
                } else if (ch == '\'') {
                    inSingleQuotes = true; // Open single quotes
                } else if (Character.isWhitespace(ch)) {
                    // Space separates arguments only when unquoted and unescaped
                    if (current.length() > 0) {
                        args.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(ch);
                }
            }
        }

        // Add the final argument if there is one remaining
        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args;
    }

    // Helper method to look up commands in the system PATH
    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

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