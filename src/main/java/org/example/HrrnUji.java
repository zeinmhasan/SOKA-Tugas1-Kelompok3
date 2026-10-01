package org.example;

import java.util.ArrayList;
import java.util.List;

public class HrrnUji {
    record Task(String nama, double arrival, double service) {}

    // RR = (waktu tunggu + waktu layanan) / waktu layanan
    static double responseRatio(Task t, double now) {
        double wait = now - t.arrival();
        return (wait + t.service()) / t.service();
    }

    public static void main(String[] args) {
        List<Task> belum = new ArrayList<>(List.of(
                new Task("P1", 0, 3), new Task("P2", 2, 6), new Task("P3", 4, 4),
                new Task("P4", 6, 5), new Task("P5", 8, 2)));

        double now = 0;
        while (!belum.isEmpty()) {
            Task pilih = null;
            double rrTerbaik = -1;
            for (Task k : belum) {
                if (k.arrival() > now) continue;   // belum datang
                double rr = responseRatio(k, now);
                if (rr > rrTerbaik) { rrTerbaik = rr; pilih = k; }
            }
            if (pilih == null) {                   // server menganggur: loncat ke kedatangan berikutnya
                now = belum.stream().mapToDouble(Task::arrival).min().getAsDouble();
                continue;
            }
            double selesai = now + pilih.service();
            System.out.printf("%s | mulai=%.0f | selesai=%.0f | tunggu=%.0f | RR saat dipilih=%.2f%n",
                    pilih.nama(), now, selesai, now - pilih.arrival(), rrTerbaik);
            belum.remove(pilih);
            now = selesai;
        }
    }
}