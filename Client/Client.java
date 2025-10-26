import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;

public class Client {
    private static final Set<Integer> REQUEST_NUMBER = Set.of(1, 5, 10, 15, 20, 25);
    private static final String[] AVAILABLE_REQUESTS = {"DATETIME","UPTIME", "MEMORY", "NETSTAT", "USERS", "PROCESSES", "EXIT"};
    private static final Set<Integer> INPUT_NUMBER = Set.of(1, 2, 3, 4, 5, 6, 7);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("EEE MMM d hh:mm:ss a z yyyy", Locale.US);

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("\nPlease enter Network Address:");
        String networkAddress = sc.nextLine();

        System.out.println("\nPlease enter Port Number:");
        int portNumber;
        while (true) {
            portNumber = sc.nextInt();
            if (portNumber >= 1025 && portNumber <= 4998) {
                break;
            }
            System.out.println("Enter a port number between 1025 and 4998: ");
        }
        while (true) {
            System.out.println("\n1 - DATETIME\n2 - UPTIME\n3 - MEMORY\n4 - NETSTAT\n5 - USERS\n6 - PROCESSES\n7 - EXIT");
            System.out.print("Please enter the client request Number: ");
            int inputNumber = 0;
            do {
                if (sc.hasNextInt()) {
                    inputNumber = sc.nextInt();
                    if (INPUT_NUMBER.contains(inputNumber)) break;
                }
                System.out.print("Please enter a valid client request Number: ");
            } while (!INPUT_NUMBER.contains(inputNumber));
            if (inputNumber == 7) return;

            String clientRequest = AVAILABLE_REQUESTS[inputNumber - 1];

            System.out.println("Please enter the number of requests that you want to send (1, 5, 10, 15, 20, 25)");
            int requestCount = sc.nextInt();
            while (!REQUEST_NUMBER.contains(requestCount)) {
                System.out.println("Please enter the number of requests that you want to send (1, 5, 10, 15, 20, 25)");
                requestCount = sc.nextInt();
            }

            Thread[] threads = new Thread[requestCount];
            ClientResult[] results = new ClientResult[requestCount];
            for (int i = 0; i < requestCount; i++) {
                int requestNumber = i + 1;
                Runnable r = new ClientRunnable(requestNumber, networkAddress, portNumber, clientRequest, results);
                Thread t = new Thread(r, "client-" + requestNumber);
                threads[i] = t;
                t.start();
            }

            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }

            long successCount = 0;
            long totalTime = 0;
            for (ClientResult r : results) {
                if (r != null && r.error == null) {
                    successCount++;
                    totalTime += Math.round(r.timeTaken());
                }
            }

            double averageTime = totalTime / successCount;

            System.out.printf(Locale.US, "\nTotal turn-around time for client requests: %dms\n", totalTime);
            System.out.printf(Locale.US, "Average turn-around time for client requests: %.1fms\n", averageTime);
        }
    }



    private static class ClientRunnable implements Runnable {
        private int requestNumber;
        private String networkAddress;
        private int portNumber;
        private String clientRequest;
        private final ClientResult[] resultsArray;

        ClientRunnable(int requestNumber, String networkAddress, int portNumber, String clientRequest, ClientResult[] resultsArray) {
            this.requestNumber = requestNumber;
            this.networkAddress = networkAddress;
            this.portNumber = portNumber;
            this.clientRequest = clientRequest;
            this.resultsArray = resultsArray;
        }

        public void run() {
            long startTime = 0L;
            long endTime = 0L;
            int bytesReceived = 0;
            String error = null;

            try (Socket socket = new Socket(networkAddress, portNumber)) {
                socket.setSoTimeout(60_000);

                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                startTime = System.nanoTime();
                bw.write(clientRequest);
                bw.write("\n");
                bw.flush();

                StringBuilder body = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    if ("END".equals(line)) break;
                    body.append(line).append("\n");
                }
                endTime = System.nanoTime();

                bytesReceived = body.toString().getBytes(StandardCharsets.UTF_8).length;
                String displayStamp = ZonedDateTime.now().format(STAMP);
                if (!"DATETIME".equals(clientRequest)) {
                    System.out.println(displayStamp);
                }
                if (body.length() > 0) {
                    System.out.print(body.toString());
                }
                System.out.printf("Turn-around time for client request number %d: %dms%n", requestNumber, Math.round((endTime - startTime) / 1_000_000.0));


                resultsArray[requestNumber - 1] = new ClientResult(requestNumber, startTime, endTime, bytesReceived, null, displayStamp, body.toString());
                return;

            } catch (Exception e) {
                error = e.getMessage();
                if (startTime == 0L) startTime = System.nanoTime();
                endTime = System.nanoTime();
            }
            resultsArray[requestNumber - 1] = new ClientResult(requestNumber, startTime, endTime, bytesReceived, error, null, null);
        }
    }
    private static class ClientResult{
        int requestNumber;
        long startTime;
        long endTime;
        int bytesReceived;
        String error;
        final String timestampLine;
        final String responseText;

        ClientResult(int requestNumber, long startTime, long endTime, int bytesReceived, String error, String timestampLine, String responseText){
            this.requestNumber = requestNumber;
            this.startTime = startTime;
            this.endTime = endTime;
            this.bytesReceived = bytesReceived;
            this.error = error;
            this.timestampLine = timestampLine;
            this.responseText = responseText;
        }

        double timeTaken() { return (endTime - startTime) / 1_000_000.0; }
    }

}