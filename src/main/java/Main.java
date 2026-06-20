import java.util.*;
import java.io.*;

public class Main {
    private static String currentDir = System.getProperty("user.dir");
    private static final Set<String> builtins = Set.of("exit", "echo", "type", "cd", "pwd");

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
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
                handlePipeline(firstCmdArgs, secondCmdArgs, originalOut, originalErr);
                continue;
            }

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
                executeCommand(commandArgs, redirectOutFile, redirectErrFile, appendOut, appendErr);
            } catch (Exception e) {
                System.out.println("Error executing command: " + e.getMessage());
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

    private static void executeCommand(List<String> args, String redirectOutFile, String redirectErrFile, boolean appendOut, boolean appendErr) throws Exception {
        String command = args.get(0);

        if (command.equals("exit")) {
            System.exit(0);
        } 
        else if (command.equals("echo")) {
            List<String> echoArgs = args.subList(1, args.size());
            System.out.println(String.join(" ", echoArgs));
        } 
        else if (command.equals("pwd")) {
            System.out.println(currentDir);
        }
        else if (command.equals("cd")) {
            String targetPath = args.size() > 1 ? args.get(1) : "~";
            
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
            if (args.size() < 2) return;
            String targetCmd = args.get(1);

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
                ProcessBuilder pb = new ProcessBuilder(args);
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
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }

    private static void handlePipeline(List<String> firstCmd, List<String> secondCmd, PrintStream origOut, PrintStream origErr) {
        if (firstCmd.isEmpty() || secondCmd.isEmpty()) return;

        boolean firstIsBuiltin = builtins.contains(firstCmd.get(0));
        boolean secondIsBuiltin = builtins.contains(secondCmd.get(0));

        String path1 = firstIsBuiltin ? "" : getPath(firstCmd.get(0));
        String path2 = secondIsBuiltin ? "" : getPath(secondCmd.get(0));

        if (!firstIsBuiltin && path1 == null) {
            System.out.println(firstCmd.get(0) + ": command not found");
            return;
        }
        if (!secondIsBuiltin && path2 == null) {
            System.out.println(secondCmd.get(0) + ": command not found");
            return;
        }

        if (!firstIsBuiltin && !secondIsBuiltin) {
            try {
                ProcessBuilder pb1 = new ProcessBuilder(firstCmd).directory(new File(currentDir));
                ProcessBuilder pb2 = new ProcessBuilder(secondCmd).directory(new File(currentDir));
                
                pb1.redirectInput(ProcessBuilder.Redirect.INHERIT);
                pb1.redirectError(ProcessBuilder.Redirect.INHERIT);
                pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                pb2.redirectError(ProcessBuilder.Redirect.INHERIT);

                List<Process> pipeline = ProcessBuilder.startPipeline(Arrays.asList(pb1, pb2));
                for (Process p : pipeline) {
                    p.waitFor();
                }
            } catch (Exception e) {
                System.out.println("Error executing pipeline: " + e.getMessage());
            }
            return;
        }

        try {
            PipedOutputStream pipeOut = new PipedOutputStream();
            PipedInputStream pipeIn = new PipedInputStream(pipeOut);

            Thread firstThread = new Thread(() -> {
                PrintStream previousOut = System.out;
                try {
                    if (firstIsBuiltin) {
                        System.setOut(new PrintStream(pipeOut, true));
                        executeCommand(firstCmd, null, null, false, false);
                    } else {
                        ProcessBuilder pb1 = new ProcessBuilder(firstCmd);
                        pb1.directory(new File(currentDir));
                        pb1.redirectInput(ProcessBuilder.Redirect.INHERIT);
                        pb1.redirectError(ProcessBuilder.Redirect.INHERIT);
                        Process p1 = pb1.start();
                        
                        try (InputStream is = p1.getInputStream()) {
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                pipeOut.write(buffer, 0, bytesRead);
                                pipeOut.flush();
                            }
                        }
                        p1.waitFor();
                    }
                } catch (Exception e) {
                } finally {
                    try { pipeOut.close(); } catch (IOException io) {}
                    System.setOut(previousOut);
                }
            });

            Thread secondThread = new Thread(() -> {
                InputStream previousIn = System.in;
                try {
                    if (secondIsBuiltin) {
                        System.setIn(pipeIn);
                        executeCommand(secondCmd, null, null, false, false);
                    } else {
                        ProcessBuilder pb2 = new ProcessBuilder(secondCmd);
                        pb2.directory(new File(currentDir));
                        pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                        
                        Process p2 = pb2.start();

                        try (OutputStream os = p2.getOutputStream()) {
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = pipeIn.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                                os.flush();
                            }
                        }
                        p2.waitFor();
                    }
                } catch (Exception e) {
                } finally {
                    try { pipeIn.close(); } catch (IOException io) {}
                    System.setIn(previousIn);
                }
            });

            firstThread.start();
            secondThread.start();

            firstThread.join();
            secondThread.join();

        } catch (Exception e) {
            System.out.println("Error executing pipeline: " + e.getMessage());
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
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