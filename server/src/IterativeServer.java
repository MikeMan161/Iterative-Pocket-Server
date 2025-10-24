import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class IterativeServer {

    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "DATETIME","UPTIME","MEMORY","NETSTAT","USERS","PROCESSES","HELP"));

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java IterativeServer <bind-address> <port>");
            System.exit(1);
        }
        String bind = args[0];
        int port = Integer.parseInt(args[1]);

        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(bind, port));
            log("Listening on " + bind + ":" + port);

            while (true) { // ITERATIVE LOOP
                try (Socket s = ss.accept()) {
                    s.setSoTimeout(30_000);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

                    String line = in.readLine();
                    String cmd = (line == null ? "" : line.trim().toUpperCase(Locale.ROOT));
                    log("Request: " + cmd);

                    String response = ALLOWED.contains(cmd) ? handle(cmd) : help("ERROR: Unknown command: " + cmd);

                    out.write(response);
                    if (!response.endsWith("\n")) out.write("\n");
                    out.write("END\n");
                    out.flush();
                } catch (SocketTimeoutException e) {
                    log("Client socket timeout.");
                } catch (IOException e) {
                    log("Client I/O error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log("Fatal: " + e.getMessage());
            System.exit(2);
        }
    }

    private static String handle(String cmd) {
        switch (cmd) {
            case "DATETIME":
                return execFirst(new String[][]{{"date"}},
                        "Server Local (Java): " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n");
            case "UPTIME":
                return execFirst(new String[][]{{"uptime","-p"}, {"uptime"}}, "uptime not available.\n");
            case "MEMORY":
                return execFirst(new String[][]{{"free","-h"}, {"free"}}, "free not available.\n");
            case "NETSTAT":
                return execFirst(new String[][]{{"netstat","-an"}, {"ss","-tuna"}}, "netstat/ss not available.\n");
            case "USERS":
                return execFirst(new String[][]{{"who"}}, "who not available.\n");
            case "PROCESSES":
                return execFirst(new String[][]{
                        {"bash","-lc","ps aux --sort=-%cpu | head -n 25"}, {"ps","aux"}}, "ps not available.\n");
            default:
                return help(null);
        }
    }

    private static String execFirst(String[][] cmds, String fallback) {
        for (String[] cmd : cmds) {
            try { return exec(cmd); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static String exec(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String L; while ((L = r.readLine()) != null) sb.append(L).append('\n');
        }
        p.waitFor();
        return sb.toString();
    }

    private static String help(String prefix) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null) sb.append(prefix).append('\n');
        sb.append("Commands:\n")
          .append("  DATETIME | UPTIME | MEMORY | NETSTAT | USERS | PROCESSES | HELP\n");
        return sb.toString();
    }

    private static void log(String m) {
        System.out.printf("[%s] %s%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), m);
    }
}

