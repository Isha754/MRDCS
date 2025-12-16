import mpi.MPI;
import mpi.Status;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Random;

public class MRDCS {

    static final int RESEARCH_NODE = 0;
    static final int MONITORING_NODE = 1;
    static final int DATA_PROCESSING_NODE = 2;
    static final int CLOUD_STORAGE_NODE = 3;
    static final int BACKUP_STORAGE_NODE = 4;

    static final int TAG_READINGS = 100;
    static final int TAG_TO_CLOUD = 200;
    static final int TAG_TO_BACKUP = 300;

    static double[] makeReading(int seq, Random rng) {
        double temp = round2(10 + rng.nextDouble() * 20);
        double salinity = round2(30 + rng.nextDouble() * 7);
        double pollution = round2(rng.nextDouble() * 25);

        // inject rare anomaly
        if (rng.nextDouble() < 0.10) pollution = 80.0;

        return new double[]{
                seq,
                Instant.now().getEpochSecond(),
                temp,
                salinity,
                pollution,
                0.0
        };
    }

    static boolean isAnomaly(double[] msg) {
        double temp = msg[2];
        double pollution = msg[4];
        return pollution > 50.0 || temp > 35.0;
    }

    static void logLine(String file, String line) {
        try (FileWriter fw = new FileWriter("logs/" + file, true)) {
            fw.write(line + "\n");
        } catch (IOException ignored) {}
    }

    static double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        if (size < 5 && rank == 0) {
            System.out.println("Run with at least 5 processes: ranks 0..4");
            MPI.Finalize();
            return;
        }


        if (rank == MONITORING_NODE) {
            runMonitoring();
        } else if (rank == DATA_PROCESSING_NODE) {
            runProcessing();
        } else if (rank == CLOUD_STORAGE_NODE) {
            runCloud();
        } else if (rank == BACKUP_STORAGE_NODE) {
            runBackup();
        } else if (rank == RESEARCH_NODE) {
            runResearch();
        }

        MPI.Finalize();
    }

    static void runMonitoring() throws InterruptedException {
        Random rng = new Random();
        int messages = 20;

        for (int seq = 0; seq < messages; seq++) {
            double[] msg = makeReading(seq, rng);

            MPI.COMM_WORLD.Send(msg, 0, msg.length, MPI.DOUBLE, DATA_PROCESSING_NODE, TAG_READINGS);
            System.out.println("[MONITOR] sent seq=" + (int) msg[0] + " pollution=" + msg[4]);
            logLine("monitor.log", toLine(msg));

            Thread.sleep(2000);
        }

        // send shutdown
        double[] shutdown = new double[]{-1, Instant.now().getEpochSecond(), 0, 0, 0, 1.0};
        MPI.COMM_WORLD.Send(shutdown, 0, shutdown.length, MPI.DOUBLE, DATA_PROCESSING_NODE, TAG_READINGS);
        System.out.println("[MONITOR] sent shutdown");
    }

    static void runProcessing() {
        while (true) {
            double[] msg = new double[6];
            Status st = MPI.COMM_WORLD.Recv(msg, 0, msg.length, MPI.DOUBLE, MONITORING_NODE, TAG_READINGS);

            boolean shutdown = msg[5] == 1.0;
            if (shutdown) {
                // forward shutdown to cloud and backup
                MPI.COMM_WORLD.Send(msg, 0, msg.length, MPI.DOUBLE, CLOUD_STORAGE_NODE, TAG_TO_CLOUD);
                MPI.COMM_WORLD.Send(msg, 0, msg.length, MPI.DOUBLE, BACKUP_STORAGE_NODE, TAG_TO_BACKUP);
                System.out.println("[PROCESS] shutdown received; forwarded");
                break;
            }

            if (isAnomaly(msg)) {
                MPI.COMM_WORLD.Send(msg, 0, msg.length, MPI.DOUBLE, CLOUD_STORAGE_NODE, TAG_TO_CLOUD);
                System.out.println("[PROCESS] anomaly seq=" + (int) msg[0] + " -> CLOUD");
                logLine("process.log", "ANOMALY " + toLine(msg));
            } else {
                MPI.COMM_WORLD.Send(msg, 0, msg.length, MPI.DOUBLE, BACKUP_STORAGE_NODE, TAG_TO_BACKUP);
                System.out.println("[PROCESS] normal seq=" + (int) msg[0] + " -> BACKUP");
                logLine("process.log", "NORMAL " + toLine(msg));
            }
        }
    }

    static void runCloud() {
        while (true) {
            double[] msg = new double[6];
            MPI.COMM_WORLD.Recv(msg, 0, msg.length, MPI.DOUBLE, DATA_PROCESSING_NODE, TAG_TO_CLOUD);

            boolean shutdown = msg[5] == 1.0;
            if (shutdown) {
                // Broadcast shutdown so research exits cleanly
                MPI.COMM_WORLD.Bcast(msg, 0, msg.length, MPI.DOUBLE, CLOUD_STORAGE_NODE);
                System.out.println("[CLOUD] shutdown; broadcasted");
                break;
            }

            logLine("cloud.log", toLine(msg));
            System.out.println("[CLOUD] stored anomaly seq=" + (int) msg[0] + " -> broadcast");

            // Broadcast anomalies to all nodes
            MPI.COMM_WORLD.Bcast(msg, 0, msg.length, MPI.DOUBLE, CLOUD_STORAGE_NODE);
        }
    }

    static void runResearch() {
        while (true) {
            double[] msg = new double[6];

            MPI.COMM_WORLD.Bcast(msg, 0, msg.length, MPI.DOUBLE, CLOUD_STORAGE_NODE);

            boolean shutdown = msg[5] == 1.0;
            if (shutdown) {
                System.out.println("[RESEARCH] shutdown received");
                break;
            }

            System.out.println("[RESEARCH] analyzing anomaly seq=" + (int) msg[0] + " pollution=" + msg[4]);
            logLine("research.log", "ANALYZED " + toLine(msg));
        }
    }

    static void runBackup() {
        try (FileWriter fw = new FileWriter("backup_storage.txt", true)) {
            while (true) {
                double[] msg = new double[6];
                MPI.COMM_WORLD.Recv(msg, 0, msg.length, MPI.DOUBLE, DATA_PROCESSING_NODE, TAG_TO_BACKUP);

                boolean shutdown = msg[5] == 1.0;
                if (shutdown) {
                    System.out.println("[BACKUP] shutdown received");
                    break;
                }

                fw.write(toLine(msg) + "\n");
                fw.flush();
                System.out.println("[BACKUP] stored normal seq=" + (int) msg[0]);
                logLine("backup.log", toLine(msg));
            }
        } catch (IOException e) {
            System.out.println("[BACKUP] file error: " + e.getMessage());
        }

      
    }

    static String toLine(double[] m) {
        return "seq=" + (int)m[0] + ", ts=" + (long)m[1] + ", temp=" + m[2] + ", sal=" + m[3] + ", pol=" + m[4] + ", shutdown=" + (int)m[5];
    }
}