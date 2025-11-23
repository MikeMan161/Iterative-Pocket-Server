import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.concurrent.*;

public class ConcurrentServer {

    private static final int CLIENT_SOCKET_TIMEOUT_MS = 60_000;
    private static final int BACKLOG = 50;
    private static final int MAX_THREADS = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java ConcurrentServer <bindAddress> <port>");
            System.exit(1);
        }
        final String bind = args[0];
        final int port = Integer.parseInt(args[1]);

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS, r -> {
            Thread t = new Thread(r);
            t.setName("worker-" + t.getId());
            t.setDaemon(false);
            return t;
        });

        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName(bind), port), BACKLOG);
            log("Listening on " + bind + ":" + port + " (threads=" + MAX_THREADS + ")");

            while (true) {
                final Socket client = server.accept();
                pool.execute(() -> handleClient(client));
            }
        } catch (IOException e) {
            log("Fatal: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void handleClient(Socket sock) {
        final String remote = sock.getRemoteSocketAddress().toString();
        try (Socket s = sock) {
            s.setSoTimeout(CLIENT_SOCKET_TIMEOUT_MS);
            BufferedReader in  = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

            String cmd = in.readLine();
            if (cmd == null) return;
            cmd = cmd.trim().toUpperCase(Locale.ROOT);
            log("[" + Thread.currentThread().getName() + "] " + remote + " -> " + cmd);

            switch (cmd) {
                case "DATETIME":
                    writeLine(out, "Server Local (Java): " + ZonedDateTime.now());
                    break;
                case "UPTIME":
                    execAndStream(out, new String[]{"uptime"});
                    break;
                case "MEMORY":
                    execAndStream(out, new String[]{"sh","-lc","free -m || free -h"});
                    break;
                case "NETSTAT":
                    execAndStream(out, new String[]{"sh","-lc","ss -tuna || netstat -an"});
                    break;
                case "USERS":
                    execAndStream(out, new String[]{"who"});
                    break;
                case "PROCESSES":
                    execAndStream(out, new String[]{"sh","-lc","ps aux --sort=-%cpu | head -n 40"});
                    break;
                default:
                    writeLine(out, "Unknown command. Try: DATETIME|UPTIME|MEMORY|NETSTAT|USERS|PROCESSES");
            }
            writeLine(out, "END");
            out.flush();
        } catch (SocketTimeoutException e) {
            log("Timeout " + remote + ": " + e.getMessage());
        } catch (IOException e) {
            log("I/O " + remote + ": " + e.getMessage());
        } catch (Exception e) {
            log("Handler error " + remote + ": " + e);
        }
    }

    private static void execAndStream(BufferedWriter out, String[] cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                writeLine(out, line);
            }
        } finally {
            try { p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
    }

    private static void writeLine(BufferedWriter out, String s) throws IOException {
        out.write(s); out.write("\n");
    }

    private static void log(String s){ System.out.printf("[%s] %s%n", java.time.LocalDateTime.now(), s); }
}
