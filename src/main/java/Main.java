import java.util.*;
import java.io.*;

public class Main {
    private static String currentDir = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        Set<String> builtins = Set.of("exit", "echo", "type", "cd", "pwd");
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

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

            // --- REDIRECTION LOGIC ---
            String redirectOutFile = null;
            String redirectErrFile = null;
            int redirectIndex = -1;

            for (int i = 0; i < inputArgs.size(); i++) {
                String arg = inputArgs.get(i);
                if (arg.equals(">") || arg.equals("1>")) {
                    if (i + 1 < inputArgs.size()) {
                        redirectOutFile = inputArgs.get(i + 1);
                        redirectIndex = i;
                        break;
                    }
                } else if (arg.equals("2>")) {
                    if (i + 1 < inputArgs.size()) {
                        redirectErrFile = inputArgs.get(i + 1);
                        redirectIndex = i;
                        break;
                    }
                }
            }

            List<String> commandArgs;
            if (redirectIndex != -1) {
                commandArgs = new ArrayList<>(inputArgs.subList(0, redirectIndex));
            } else {
                commandArgs = new ArrayList<>(inputArgs);
            }

            if (commandArgs.isEmpty()) {
                continue;
            }

            String command = commandArgs.get(0);

            // Set up Redirections for Builtin Commands (and ensure files are created upfront)
            if (redirectOutFile != null) {
                File file = new File(redirectOutFile);
                if (!file.isAbsolute()) {
                    file = new File(currentDir, redirectOutFile);
                }
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                System.setOut(new PrintStream(new FileOutputStream(file, false)));
            }

            if (redirectErrFile != null) {
                File file = new File(redirectErrFile);
                if (!file.isAbsolute()) {
                    file = new File(currentDir, redirectErrFile);
                }
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                // Redirect System.err for builtins, which also forces file creation
                System.setErr(new PrintStream(new FileOutputStream(file, false)));
            }

            try {
                // 1. Handle "exit"
                if (command.equals("exit")) {
                    System.exit(0);
                } 
                // 2. Handle "echo"
                else if (command.equals("echo")) {
                    List<String> echoArgs = commandArgs.subList(1, commandArgs.size());
                    System.out.println(String.join(" ", echoArgs));
                } 
                // 3. Handle "pwd"
                else if (command.equals("pwd")) {
                    System.out.println(currentDir);
                }
                // 4. Handle "cd"
                else if (command.equals("cd")) {
                    String targetPath = commandArgs.size() > 1 ? commandArgs.get(1) : "~";
                    
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
                    if (commandArgs.size() < 2) continue;
                    String targetCmd = commandArgs.get(1);

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
                            ProcessBuilder pb = new ProcessBuilder(commandArgs);
                            pb.directory(new File(currentDir));
                            
                            if (redirectOutFile != null) {
                                File file = new File(redirectOutFile);
                                if (!file.isAbsolute()) file = new File(currentDir, redirectOutFile);
                                pb.redirectOutput(ProcessBuilder.Redirect.to(file));
                            } else {
                                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            }

                            if (redirectErrFile != null) {
                                File file = new File(redirectErrFile);
                                if (!file.isAbsolute()) file = new File(currentDir, redirectErrFile);
                                pb.redirectError(ProcessBuilder.Redirect.to(file));
                            } else {
                                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                            }

                            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                            
                            Process process = pb.start();
                            process.waitFor(); 
                        } catch (Exception e) {
                            System.out.println("Error executing command: " + e.getMessage());
                        }
                    } else {
                        System.out.println(command + ": command not found");
                    }
                }
            } finally {
                // Restore original system output streams completely
                if (redirectOutFile != null) {
                    System.out.flush();
                    System.setOut(originalOut);
                }
                if (redirectErrFile != null) {
                    System.err.flush();
                    System.setErr(originalErr);
                }
            }
        }
    }

    private static List<String> parseCommand(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean isEscaped = false; 

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (isEscaped) {
                current.append(ch);
                isEscaped = false; 
                continue;
            }

            if (inDoubleQuotes) {
                if (ch == '\\') {
                    if (i + 1 < input.length()) {
                        char nextCh = input.charAt(i + 1);
                        if (nextCh == '"' || nextCh == '\\' || nextCh == '$' || nextCh == '`') {
                            current.append(nextCh);
                            i++; 
                        } else {
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
            else if (inSingleQuotes) {
                if (ch == '\'') {
                    inSingleQuotes = false; 
                } else {
                    current.append(ch);
                }
            }
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