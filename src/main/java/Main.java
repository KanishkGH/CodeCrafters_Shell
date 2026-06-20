import java.util.*;
import java.io.*;

public class Main {
    private static String currentDir = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
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
                    if (!file.isAbsolute()) {
                        file = new File(currentDir, targetPath);
                    }
                    
                    if (file.exists() && file.isDirectory()) {
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
                        pb.directory(new File(currentDir));
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

    // Upgraded to handle selective backslash escaping inside double quotes
    private static List<String> parseCommand(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean isEscaped = false; 

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            // Handle unquoted backslash escaping short-circuit
            if (isEscaped) {
                current.append(ch);
                isEscaped = false; 
                continue;
            }

            // 1. Double Quotes Context
            if (inDoubleQuotes) {
                if (ch == '\\') {
                    // Check if there's a character following the backslash
                    if (i + 1 < input.length()) {
                        char nextCh = input.charAt(i + 1);
                        // Inside double quotes, only escape ", \, $, and `
                        if (nextCh == '"' || nextCh == '\\' || nextCh == '$' || nextCh == '`') {
                            current.append(nextCh);
                            i++; // Skip processing the escaped character on the next iteration
                        } else {
                            // If it's anything else (like \n), treat the backslash literally
                            current.append(ch);
                        }
                    } else {
                        current.append(ch);
                    }
                } else if (ch == '"') {
                    inDoubleQuotes = false; 
                } else {
                    current.append(ch);
                }
            }
            // 2. Single Quotes Context
            else if (inSingleQuotes) {
                if (ch == '\'') {
                    inSingleQuotes = false; 
                } else {
                    current.append(ch);
                }
            }
            // 3. Unquoted Context
            else {
                if (ch == '\\') {
                    isEscaped = true; 
                } else if (ch == '"') {
                    inDoubleQuotes = true; 
                } else if (ch == '\'') {
                    inSingleQuotes = true; 
                } else if (Character.isWhitespace(ch)) {
                    if (current.length() > 0) {
                        args.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(ch);
                }
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args;
    }

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