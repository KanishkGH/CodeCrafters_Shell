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

            // Check for pipeline (|)
            int pipeIndex = -1;
            for (int i = 0; i < inputArgs.size(); i++) {
                if (inputArgs.get(i).equals("|")) {
                    pipeIndex = i;
                    break;
                }
            }

            if (pipeIndex != -1) {
                List<String> firstCmdArgs = inputArgs.subList(0, pipeIndex);
                List<String> secondCmdArgs = inputArgs.subList(pipeIndex + 1, inputArgs.size());
                handlePipeline(firstCmdArgs, secondCmdArgs);
                continue;
            }

            // --- STANDARD REDIRECTION LOGIC ---
            String redirectOutFile = null;
            String redirectErrFile = null;
            boolean appendOut = false; 
            boolean appendErr = false; 
            int redirectIndex = -1;

            for (int i = 0; i < inputArgs.size(); i++) {
                String arg = inputArgs.get(i);
                if (arg.equals(">") || arg.equals("1>")) {
                    if (i + 1 < inputArgs.size()) {
                        redirectOutFile = inputArgs.get(i + 1);
                        appendOut = false;
                        redirectIndex = i;
                        break;
                    }
                } else if (arg.equals(">>") || arg.equals("1>>")) {
                    if (i + 1 < inputArgs.size()) {
                        redirectOutFile = inputArgs.get(i + 1);
                        appendOut = true;
                        redirectIndex = i;
                        break;
                    }
                } else if (arg.equals("2>")) {
                    if (i + 1 < inputArgs.size()) {
                        redirectErrFile = inputArgs.get(i + 1);
                        appendErr = false;
                        redirectIndex = i;
                        break;
                    }
                } else if (arg.equals("2>>")) {
                    if (i + 1 < inputArgs.size()) {
                        redirectErrFile = inputArgs.get(i + 1);
                        appendErr = true;
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

            if (redirectOutFile != null) {
                File file = new File(redirectOutFile);
                if (!file.isAbsolute()) {
                    file = new File(currentDir, redirectOutFile);
                }
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                System.setOut(new PrintStream(new FileOutputStream(file, appendOut)));
            }

            if (redirectErrFile != null) {
                File file = new File(redirectErrFile);
                if (!file.isAbsolute()) {
                    file = new File(currentDir, redirectErrFile);
                }
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                System.setErr(new PrintStream(new FileOutputStream(file, appendErr)));
            }

            try {
                if (command.equals("exit")) {
                    System.exit(0);
                } 
                else if (command.equals("echo")) {
                    List<String> echoArgs = commandArgs.subList(1, commandArgs.size());
                    System.out.println(String.join(" ", echoArgs));
                } 
                else if (command.equals("pwd")) {
                    System.out.println(currentDir);
                }
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
                else {
                    String path = getPath(command);
                    if (path != null) {
                        try {
                            ProcessBuilder pb = new ProcessBuilder(commandArgs);
                            pb.directory(new File(currentDir));
                            
                            if (redirectOutFile != null) {
                                File file = new File(redirectOutFile);
                                if (!file.isAbsolute()) file = new File(currentDir, redirectOutFile);
                                if (appendOut) {
                                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(file));
                                } else {
                                    pb.redirectOutput(ProcessBuilder.Redirect.to(file));
                                }
                            } else {
                                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            }

                            if (redirectErrFile != null) {
                                File file = new File(redirectErrFile);
                                if (!file.isAbsolute()) file = new File(currentDir, redirectErrFile);
                                if (appendErr) {
                                    pb.redirectError(ProcessBuilder.Redirect.appendTo(file));
                                } else {
                                    pb.redirectError(ProcessBuilder.Redirect.to(file));
                                }
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

    private static void handlePipeline(List<String> firstCmd, List<String> secondCmd) {
        if (firstCmd.isEmpty() || secondCmd.isEmpty()) return;

        String path1 = getPath(firstCmd.get(0));
        String path2 = getPath(secondCmd.get(0));

        if (path1 == null) {
            System.out.println(firstCmd.get(0) + ": command not found");
            return;
        }
        if (path2 == null) {
            System.out.println(secondCmd.get(0) + ": command not found");
            return;
        }

        try {
            ProcessBuilder pb1 = new ProcessBuilder(firstCmd);
            ProcessBuilder pb2 = new ProcessBuilder(secondCmd);

            pb1.directory(new File(currentDir));
            pb2.directory(new File(currentDir));

            pb1.redirectInput(ProcessBuilder.Redirect.INHERIT);
            pb1.redirectError(ProcessBuilder.Redirect.INHERIT);

            pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb2.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process p1 = pb1.start();
            Process p2 = pb2.start();

            // Establish thread-based pipe connection to keep streaming alive actively
            Thread pipeThread = new Thread(() -> {
                try (InputStream input = p1.getInputStream();
                     OutputStream output = p2.getOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                        output.flush();
                    }
                } catch (IOException e) {
                    // Pipeline closure handled implicitly
                }
            });

            pipeThread.start();

            p1.waitFor();
            p2.waitFor();
            pipeThread.join();

        } catch (Exception e) {
            System.out.println("Error executing pipeline: " + e.getMessage());
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