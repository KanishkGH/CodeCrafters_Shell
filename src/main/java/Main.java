import java.io.*;
import java.util.*;

public class Main {

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        String[] paths = pathEnv.split(File.pathSeparator);

        for (String dir : paths) {
            File file = new File(dir, command);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine();

            if (input.equals("exit")) {
                break;
            }

            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
                continue;
            }

            if (input.startsWith("type ")) {
                String cmd = input.substring(5);

                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")) {
                    System.out.println(cmd + " is a shell builtin");
                    continue;
                }

                String executable = findExecutable(cmd);

                if (executable != null) {
                    System.out.println(cmd + " is " + executable);
                } else {
                    System.out.println(cmd + ": not found");
                }

                continue;
            }

            String[] parts = input.split(" ");
            String executable = findExecutable(parts[0]);

            if (executable != null) {
                List<String> command = new ArrayList<>();
                command.add(executable);
                command.addAll(Arrays.asList(parts).subList(1, parts.length));

                Process process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }

                process.waitFor();
            } else {
                System.out.println(input + ": command not found");
            }
        }
    }
}