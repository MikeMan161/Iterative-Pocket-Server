import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Client {
    private static final Set<Integer> REQUEST_NUMBER = Set.of(1, 5, 10, 15, 20, 25);
    private static final String[] AVAILABLE_REQUESTS = {"DATETIME","UPTIME", "MEMORY", "NETSTAT", "USERS", "PROCESSES", "EXIT"};
    private static final Set<Integer> INPUT_NUMBER = Set.of(1, 2, 3, 4, 5, 6, 7, 8);
    private static final DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d hh:mm:ss a z yyyy", Locale.US);

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("Please enter Network Address:\n");
        String networkAddress = sc.nextLine();

        System.out.println("Please enter Port Number:\n");
        boolean flag = true;
        int portNumber = 0;
        while (flag) {
            portNumber = sc.nextInt();
            if (portNumber > 1025 || portNumber < 4998) {
                flag = false;
                break;
            }
            System.out.println("Enter a port number between 1 and 4998: ");
        }
        while (true) {
            System.out.println("1 - DATETIME\n2 - UPTIME\n3 - MEMORY\n4 - NETSTAT\n5 - USERS\n6 - PROCESSES\n7 - EXIT");
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
            long t0 = System.nanoTime();

            for (int i = 0; i < requestCount; i++) {
                int id = i + 1;
                Runnable r = new ClientRunnable(id, networkAddress, portNumber, clientRequest, results);
                Thread t = new Thread(r, "client-" + id);
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
            long t1 = System.nanoTime();

            List<ClientResult> resultList = new ArrayList<>(requestCount);
            for (ClientResult r : results) {
                if (r != null) resultList.add(r);
            }
            resultList.sort(Comparator.comparingInt(r -> r.id));

            System.out.println("\nPer-attempt Turn-around Times (ms): ");
            System.out.println("ID, RTT(ms), Bytes, Error");
            long successCount = 0;
            long totalMillis = 0;
//            for (ClientResult r : resultList) {
//                System.out.printf(Locale.US, "%d, %.3f, %d, %s\n", r.id, r.millis(), r.bytesReceived, r.error == null ? "" : r.error);
//                if (r.error == null) {
//                    successCount++;
//                    totalMillis += Math.round(r.millis());
//                }
//            }
            for  (ClientResult r : resultList) {
                if (r.timestampLine != null) {
                    System.out.println(r.timestampLine);
                }
                if (r.responseText != null && !r.responseText.isEmpty()) {
                    System.out.println(r.responseText);
                }
                System.out.printf("Turn-around time for client request number %d: %dms%n", r.id, Math.round(r.millis()));
                //System.out.printf(Locale.US, "ID, RTT(ms), Bytes, Error -> %d, %.3f, %d, %s%n",
                //        r.id, r.millis(), r.bytesReceived, r.error == null ? "" : r.error);

                if (r.error == null) {
                    successCount++;
                    totalMillis += Math.round(r.millis());
                }
            }

            double averageTime = totalMillis / successCount;
            double wallClockMs = (t1 - t0) / 1_000_000.0;

            System.out.printf(Locale.US, "\nTotal Turn-around Time (sum of per-request RTTs): %d ms\n", totalMillis);
            System.out.printf(Locale.US, "Average Turn-around Time: %.3f ms\n", averageTime);
            System.out.printf(Locale.US, "Wall-clock elapsed (submit -> all done): %.3f ms\n", wallClockMs);
            System.out.printf("Successful requests: %d / %d\n", successCount, requestCount);
            System.out.println("\nDone.");
        }
    }



    private static class ClientRunnable implements Runnable {
        private int id;
        private String networkAddress;
        private int portNumber;
        private String clientRequest;
        private final ClientResult[] sink;

        ClientRunnable(int id, String networkAddress, int portNumber, String clientRequest, ClientResult[] sink){
            this.id = id;
            this.networkAddress = networkAddress;
            this.portNumber = portNumber;
            this.clientRequest = clientRequest;
            this.sink = sink;
        }

        public void run() {
            long startTime = 0L;
            long endTime = 0L;
            int bytesReceived = 0;
            String error = null;

            try (Socket socket = new Socket(networkAddress, portNumber)){
                socket.setSoTimeout(60_000);

                OutputStream os = socket.getOutputStream();
                InputStream is = socket.getInputStream();
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
                BufferedInputStream bis = new BufferedInputStream(is);
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

                startTime = System.nanoTime();
                bw.write(clientRequest + "\n");
                bw.flush();

                byte[] buffer = new byte[8192];
                int n;
                while ((n = bis.read(buffer)) != -1){
                    bytesReceived += n;
                }

                StringBuilder body = new StringBuilder();
                Strig line;
                while ((line = br.readLine()) != null){
                    if ("END".equals(line)) break;
                    body.append(line).append('\n');
                }

                endTime = System.nanoTime();

                bytesReceived = body.toString().getBytes(StandardCharsets.UTF_8).length;
                String displayStamp = ZonedDataTime.now().format(STAMP);
                sinl[id - 1] = new ClientResult(id, startTime, endTime, bytesReceived, nuull, displayStamp, body.toString());
                return;

            } catch (Exception e){
                error = e.getMessage();
                if (startTime == 0L){
                    startTime = System.nanoTime();
                }
                endTime = System.nanoTime();
            }

            sink[id - 1] = new ClientResult(id, startTime, endTime, bytesReceived, error);
        }
    }
    private static class ClientResult{
        int id;
        long startTime;
        long endTime;
        int bytesReceived;
        String error;
        final String timestampLine;
        final String responseText;

        ClientResult(int id, long startTime, long endTime, int bytesReceived, String error, String timestampLine, String responseText){
            this.id = id;
            this.startTime = startTime;
            this.endTime = endTime;
            this.bytesReceived = bytesReceived;
            this.error = error;
            this.timestampLine = timestampLine;
            this.responseText = responseText;
        }

        double millis() { return (endTime - startTime) / 1_000_000.0; }
    }

}