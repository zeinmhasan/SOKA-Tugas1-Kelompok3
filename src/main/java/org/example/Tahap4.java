package org.example;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;

import java.util.*;
import java.util.stream.Collectors;

public class Tahap4 {
    static CloudSimPlus sim;
    static DatacenterBrokerSimple broker;
    static final List<Cloudlet> menunggu = new ArrayList<>();
    static final List<Vm> vmBebas = new ArrayList<>();
    static final Map<Cloudlet, Double> arrival = new IdentityHashMap<>();
    static final Map<Cloudlet, Double> deadline = new IdentityHashMap<>();
    static final Set<Cloudlet> pemicu = Collections.newSetFromMap(new IdentityHashMap<>());
    static double mipsAcuan;
    static final String KEBIJAKAN = System.getProperty("policy", "hrrn");

    public static void main(String[] args) {
        sim = new CloudSimPlus();

        new DatacenterSimple(sim, List.of(
                Tahap1.createHost(4, 16), Tahap1.createHost(4, 16),
                Tahap1.createHost(8, 32), Tahap1.createHost(8, 32),
                Tahap1.createHost(16, 64)));
        new DatacenterSimple(sim, List.of(
                Tahap1.createHost(4, 16),
                Tahap1.createHost(8, 32), Tahap1.createHost(8, 32),
                Tahap1.createHost(16, 64), Tahap1.createHost(16, 64)));

        broker = new DatacenterBrokerSimple(sim);
        broker.setVmDestructionDelayFunction(v -> 1000.0);

        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < 4; i++) vms.add(Tahap1.createVm(2000, 8, 16));
        for (int i = 0; i < 8; i++) vms.add(Tahap1.createVm(1500, 4, 8));
        for (int i = 0; i < 8; i++) vms.add(Tahap1.createVm(1000, 2, 4));
        mipsAcuan = vms.stream().mapToDouble(Vm::getMips).average().orElse(1000);

        int n = Integer.getInteger("n", 60);
        var rndLen = new Random(42);
        var rndArr = new Random(Long.getLong("seed", 7));
        var rndDl = new Random(99);
        List<Cloudlet> semuaPemicu = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            long length;
            int r = rndLen.nextInt(10);
            if (r < 5)      length = 5_000 + rndLen.nextInt(15_000);
            else if (r < 8) length = 20_000 + rndLen.nextInt(40_000);
            else            length = 60_000 + rndLen.nextInt(90_000);

            var c = new CloudletSimple(length, 1, new UtilizationModelFull());
            c.setFileSize(300).setOutputSize(300);
            c.setUtilizationModelRam(new UtilizationModelDynamic(0.05));
            c.setUtilizationModelBw(new UtilizationModelDynamic(0.05));

            double a = rndArr.nextInt(600) / 10.0;
            double service = length / mipsAcuan;
            arrival.put(c, a);
            deadline.put(c, a + (3 + rndDl.nextDouble() * 5) * service);
            menunggu.add(c);

            // cloudlet pemicu: membangunkan scheduler saat task ini datang
            var t = new CloudletSimple(1, 1, new UtilizationModelFull());
            t.setFileSize(1).setOutputSize(1);
            t.setUtilizationModelRam(new UtilizationModelDynamic(0.01));
            t.setUtilizationModelBw(new UtilizationModelDynamic(0.01));
            t.setVm(vms.get(0));
            t.setSubmissionDelay(a);
            t.addOnFinishListener(info -> dispatch());
            pemicu.add(t);
            semuaPemicu.add(t);
        }

        broker.submitVmList(vms);
        broker.submitCloudletList(semuaPemicu);
        vmBebas.addAll(vms);
        broker.addOnVmsCreatedListener(info -> dispatch());
        sim.start();

        laporanLama(vms);
        laporanTambahan(vms);
    }

    static void dispatch() {
        while (!vmBebas.isEmpty()) {
            List<Cloudlet> siap = menunggu.stream()
                    .filter(c -> arrival.get(c) <= sim.clock() + 1e-9).toList();
            if (siap.isEmpty()) return;
            Vm vm = pilihVm();
            Cloudlet c = pilih(siap);
            menunggu.removeIf(x -> x == c);
            vmBebas.removeIf(x -> x == vm);
            c.setVm(vm);
            c.addOnFinishListener(info -> {
                vmBebas.add(vm);
                dispatch();
            });
            broker.submitCloudlet(c);
        }
    }

    static Cloudlet pilih(List<Cloudlet> siap) {
        switch (KEBIJAKAN) {
            case "fcfs": return siap.stream().min(Comparator.comparingDouble(arrival::get)).get();
            case "sjf":  return siap.stream().min(Comparator.comparingLong(Cloudlet::getLength)).get();
            default:     return siap.stream().max(Comparator.comparingDouble(Tahap4::responseRatio)).get();
        }
    }

    // RR = (waktu tunggu + service time) / service time
    static double responseRatio(Cloudlet c) {
        double tunggu = sim.clock() - arrival.get(c);
        double service = c.getLength() / mipsAcuan;
        return (tunggu + service) / service;
    }

    static void laporanLama(List<Vm> vms) {
        var real = broker.getCloudletFinishedList().stream()
                .filter(c -> !pemicu.contains(c)).toList();
        double makespan = real.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        double resp = real.stream().mapToDouble(c -> c.getFinishTime() - arrival.get(c)).average().orElse(0);
        double wait = real.stream().mapToDouble(c -> c.getStartTime() - arrival.get(c)).average().orElse(0);
        long telat = real.stream().filter(c -> c.getFinishTime() > deadline.get(c)).count();

        Map<Long, Double> busyMap = real.stream().collect(Collectors.groupingBy(
                c -> c.getVm().getId(),
                Collectors.summingDouble(c -> c.getFinishTime() - c.getStartTime())));
        double[] busy = vms.stream().mapToDouble(v -> busyMap.getOrDefault(v.getId(), 0.0)).toArray();
        double rata = Arrays.stream(busy).average().orElse(0);
        double std = Math.sqrt(Arrays.stream(busy).map(b -> (b - rata) * (b - rata)).average().orElse(0));
        double util = Arrays.stream(busy).sum() / (vms.size() * makespan) * 100;

        System.out.printf("HASIL %-4s | selesai=%d | makespan=%.2f | resp=%.2f | tunggu=%.2f | throughput=%.3f task/s | util=%.1f%% | imbalance=%.2f | lewat deadline=%d%n",
                KEBIJAKAN, real.size(), makespan, resp, wait, real.size() / makespan, util,
                rata == 0 ? 0 : std / rata, telat);
    }

     static void laporanTambahan(List<Vm> vms) {
        var real = broker.getCloudletFinishedList().stream()
                .filter(c -> !pemicu.contains(c)).toList();
        double makespan = real.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        double maxWait = real.stream()
                .mapToDouble(c -> c.getStartTime() - arrival.get(c)).max().orElse(0);

        // Energi: idle semua host sepanjang makespan + daya dinamis per core sibuk
        int[] coreHost = {4, 4, 8, 8, 16, 4, 8, 8, 16, 16};
        double idleTotalW = 0;
        for (int k : coreHost) idleTotalW += 40 + 5 * k;
        double joule = idleTotalW * makespan;
        for (Cloudlet c : real) {
            int k = (int) c.getVm().getHost().getPesNumber();
            double dayaDinamisPerCore = ((100 + 10.0 * k) - (40 + 5.0 * k)) / k;
            joule += dayaDinamisPerCore * (c.getFinishTime() - c.getStartTime());
        }

        System.out.printf("EKSTRA n=%d seed=%s %s | makespan=%.2f | wait_max=%.2f | energi_Wh=%.1f%n",
                Integer.getInteger("n", 60), System.getProperty("seed", "7"),
                KEBIJAKAN, makespan, maxWait, joule / 3600.0);
    }

    static final String ATURAN_VM = System.getProperty("vmRule", "fast");

    static Vm pilihVm() {
        if (ATURAN_VM.equals("green")) {
            return vmBebas.stream().min(Comparator.comparingDouble((Vm v) -> {
                int k = (int) v.getHost().getPesNumber();
                double dayaPerCore = (60.0 + 5.0 * k) / k;   // daya dinamis per core (W)
                return dayaPerCore / v.getMips();            // energi dinamis per MI
            })).get();
        }
        return vmBebas.stream().max(Comparator.comparingDouble(Vm::getMips)).get();
    }
}