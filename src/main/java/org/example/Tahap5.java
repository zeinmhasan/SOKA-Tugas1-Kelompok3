package org.example;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

// Eksperimen revisi: tiap ukuran workload dijalankan beberapa kali per algoritma, lalu dirata-rata
public class Tahap5 {
    static final String[] KEBIJAKAN = {"fcfs", "sjf", "hrrn"};

    public static void main(String[] args) {
        Log.setLevel(Level.ERROR);

        int[] ukuran = Arrays.stream(System.getProperty("ns", "60,200,1000,5000,10000").split(","))
                .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        int ulangan = Integer.getInteger("runs", 3);
        String aturanVm = System.getProperty("vmRule", "fast");

        List<String> ringkasan = new ArrayList<>();
        for (int n : ukuran) {
            for (String k : KEBIJAKAN) {
                List<Tahap4.Hasil> hasil = new ArrayList<>();
                for (int seed = 1; seed <= ulangan; seed++) {
                    Tahap4.Hasil h = Tahap4.jalankan(k, aturanVm, n, seed);
                    if (h.selesai() != n)
                        throw new IllegalStateException("n=" + n + " " + k + " seed=" + seed
                                + ": hanya " + h.selesai() + " cloudlet selesai");
                    hasil.add(h);
                    System.out.println(baris("RUN ", n, k, "seed=" + seed, List.of(h)));
                }
                String rata = baris("RATA", n, k, "runs=" + ulangan, hasil);
                System.out.println(rata);
                ringkasan.add(rata);
            }
        }

        System.out.println();
        System.out.println("=== RATA-RATA " + ulangan + " SIMULASI PER ALGORITMA ===");
        ringkasan.forEach(System.out::println);
    }

    static String baris(String label, int n, String kebijakan, String ket, List<Tahap4.Hasil> hasil) {
        return String.format("%s n=%-5d %-4s %-6s | makespan=%.2f | resp=%.2f | tunggu=%.2f | tunggu_maks=%.2f | throughput=%.3f task/s | util=%.1f%% | imbalance=%.2f | lewat deadline=%.1f | energi_Wh=%.1f",
                label, n, kebijakan, ket,
                rata(hasil, Tahap4.Hasil::makespan), rata(hasil, Tahap4.Hasil::resp),
                rata(hasil, Tahap4.Hasil::tunggu), rata(hasil, Tahap4.Hasil::tungguMaks),
                rata(hasil, Tahap4.Hasil::throughput), rata(hasil, Tahap4.Hasil::util),
                rata(hasil, Tahap4.Hasil::imbalance), rata(hasil, Tahap4.Hasil::telat),
                rata(hasil, Tahap4.Hasil::energiWh));
    }

    static double rata(List<Tahap4.Hasil> hasil, ToDoubleFunction<Tahap4.Hasil> f) {
        return hasil.stream().mapToDouble(f).average().orElse(0);
    }
}
