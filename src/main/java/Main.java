import java.util.*;
import java.io.*;

public class Main {
    private static String currentDir = System.getProperty("user.dir");
    private static final Set<String> builtins = Set.of("exit", "echo", "type", "cd", "pwd", "jobs");
    
    private static class Job {
        int id;
        long pid;
        String command;
        String status;
        Process process;

        Job(int id, long pid, String command, String status, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.process = process;
        }
    }

    private static final List<Job> activeJobs = new ArrayList<>();
    private static int nextJobId = 1;

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

            boolean isBackground = false;
            if (inputArgs.get(inputArgs.size() - 1).equals("&")) {
                isBackground = true;
                inputArgs = new ArrayList<>(inputArgs.subList(0, inputArgs.size() - 1));
            }

            if (inputArgs.isEmpty()) {
                continue;
            }

            boolean hasPipe = false;
            for (String arg : inputArgs) {
                if (arg.equals("|")) {
                    hasPipe = true;
                    break;
                }
            }

            if (hasPipe) {
                List<List<String>> pipelineStages = new ArrayList<>();
                List<String> currentStage = new ArrayList<>();
                for (String arg : inputArgs) {
                    if (arg.equals("|")) {
                        pipelineStages.add(currentStage);
                        currentStage = new ArrayList<>();
                    } else {
                        currentStage.add(arg);
                    }
                }
                pipelineStages.add(currentStage);

                handleMultiPipeline(pipelineStages, originalOut, originalErr);
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
                executeCommand(commandArgs, redirectOutFile, redirectErrFile, appendOut, appendErr, isBackground, input);
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

    private static void executeCommand(List<String> args, String redirectOutFile, String redirectErrFile, boolean appendOut, boolean appendErr, boolean isBackground, String originalCommand) throws Exception {
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
        else if (command.equals("jobs")) {
            // Reap exited processes seamlessly across all tracked references
            for (Job job : activeJobs) {
                if (job.status.equals("Running") && !job.process.isAlive()) {
                    job.status = "Done";
                    if (job.command.endsWith(" &")) {
                        job.command = job.command.substring(0, job.command.length() - 2);
                    }
                }
            }

            int size = activeJobs.size();
            for (int i = 0; i < size; i++) {
                Job job = activeJobs.get(i);
                char marker = ' ';
                if (i == size - 1) {
                    marker = '+';
                } else if (i == size - 2) {
                    marker = '-';
                }
                String paddedStatus = String.format("%-24s", job.status);
                System.out.println("[" + job.id + "]" + marker + "  " + paddedStatus + job.command);
            }

            // Purge and finalize cleanup allocations for subsequent runs 
            activeJobs.removeIf(job -> job.status.equals("Done"));
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

                if (isBackground) {
                    long pid = process.pid();
                    System.out.println("[" + nextJobId + "] " + pid);
                    activeJobs.add(new Job(nextJobId, pid, originalCommand, "Running", process));
                    nextJobId++;
                } else {
                    process.waitFor();
                }
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }

    private static void handleMultiPipeline(List<List<String>> stages, PrintStream origOut, PrintStream origErr) {
        int numStages = stages.size();
        
        boolean allExternal = true;
        for (List<String> stage : stages) {
            if (stage.isEmpty()) return;
            String cmd = stage.get(0);
            if (builtins.contains(cmd)) {
                allExternal = false;
            } else if (getPath(cmd) == null) {
                System.out.println(cmd + ": command not found");
                return;
            }
        }

        if (allExternal) {
            try {
                List<ProcessBuilder> pbs = new ArrayList<>();
                for (List<String> stage : stages) {
                    ProcessBuilder pb = new ProcessBuilder(stage).directory(new File(currentDir));
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    pbs.add(pb);
                }
                pbs.get(0).redirectInput(ProcessBuilder.Redirect.INHERIT);
                pbs.get(pbs.size() - 1).redirectOutput(ProcessBuilder.Redirect.INHERIT);

                List<Process> processes = ProcessBuilder.startPipeline(pbs);
                for (Process p : processes) {
                    p.waitFor();
                }
            } catch (Exception e) {
                System.out.println("Error executing pipeline: " + e.getMessage());
            }
            return;
        }

        try {
            List<PipedOutputStream> pipeOutputs = new ArrayList<>();
            List<PipedInputStream> pipeInputs = new ArrayList<>();
            
            for (int i = 0; i < numStages - 1; i++) {
                PipedOutputStream out = new PipedOutputStream();
                PipedInputStream in = new PipedInputStream(out);
                pipeOutputs.add(out);
                pipeInputs.add(in);
            }

            List<Thread> threads = new ArrayList<>();

            for (int i = 0; i < numStages; i++) {
                final int index = i;
                List<String> currentStage = stages.get(index);
                boolean isBuiltin = builtins.contains(currentStage.get(0));

                Thread stageThread = new Thread(() -> {
                    PrintStream previousOut = System.out;
                    InputStream previousIn = System.in;
                    try {
                        if (index > 0) {
                            System.setIn(pipeInputs.get(index - 1));
                        }
                        if (index < numStages - 1) {
                            System.setOut(new PrintStream(pipeOutputs.get(index), true));
                        } else {
                            System.setOut(origOut);
                        }

                        if (isBuiltin) {
                            executeCommand(currentStage, null, null, false, false, false, "");
                        } else {
                            ProcessBuilder pb = new ProcessBuilder(currentStage);
                            pb.directory(new File(currentDir));
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                            if (index == 0) {
                                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                            }
                            if (index == numStages - 1) {
                                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            }

                            Process p = pb.start();

                            if (index > 0) {
                                Thread inputPump = new Thread(() -> {
                                    try (InputStream pi = pipeInputs.get(index - 1);
                                         OutputStream os = p.getOutputStream()) {
                                        byte[] buffer = new byte[1024];
                                        int read;
                                        while ((read = pi.read(buffer)) != -1) {
                                            os.write(buffer, 0, read);
                                            os.flush();
                                        }
                                    } catch (IOException e) {}
                                });
                                inputPump.start();
                            }

                            if (index < numStages - 1) {
                                try (InputStream is = p.getInputStream();
                                     PipedOutputStream po = pipeOutputs.get(index)) {
                                    byte[] buffer = new byte[1024];
                                    int read;
                                    while ((read = is.read(buffer)) != -1) {
                                        po.write(buffer, 0, read);
                                        po.flush();
                                    }
                                }
                            }

                            p.waitFor();
                        }
                    } catch (Exception e) {
                    } finally {
                        if (index < numStages - 1) {
                            try { pipeOutputs.get(index).close(); } catch (IOException io) {}
                        }
                        if (index > 0) {
                            try { pipeInputs.get(index - 1).close(); } catch (IOException io) {}
                        }
                        System.setOut(previousOut);
                        System.setIn(previousIn);
                    }
                });
                threads.add(stageThread);
            }

            for (Thread t : threads) {
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }

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