package org.example;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Tahap3b {
    static CloudSimPlus sim;
    static DatacenterBrokerSimple broker;
    static final List<Cloudlet> menunggu = new ArrayList<>();
    static final List<Vm> vmBebas = new ArrayList<>();
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
        broker.setVmDestructionDelayFunction(v -> Double.parseDouble(System.getProperty("delayVm", "1000")));

        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < 4; i++) vms.add(Tahap1.createVm(2000, 8, 16));
        for (int i = 0; i < 8; i++) vms.add(Tahap1.createVm(1500, 4, 8));
        for (int i = 0; i < 8; i++) vms.add(Tahap1.createVm(1000, 2, 4));
        mipsAcuan = vms.stream().mapToDouble(Vm::getMips).average().orElse(1000);

        // Workload sama seperti Tahap 2/3a (seed 42), semua datang di detik 0
        int n = Integer.getInteger("n", 60);
        var rnd = new Random(42);
        for (int i = 0; i < n; i++) {
            long length;
            int r = rnd.nextInt(10);
            if (r < 5)      length = 5_000 + rnd.nextInt(15_000);
            else if (r < 8) length = 20_000 + rnd.nextInt(40_000);
            else            length = 60_000 + rnd.nextInt(90_000);
            var c = new CloudletSimple(length, 1, new UtilizationModelFull());
            c.setFileSize(300).setOutputSize(300);
            c.setUtilizationModelRam(new UtilizationModelDynamic(0.05));
            c.setUtilizationModelBw(new UtilizationModelDynamic(0.05));
            menunggu.add(c);
        }

        broker.submitVmList(vms);
        vmBebas.addAll(vms);
        broker.addOnVmsCreatedListener(info -> dispatch());
        sim.start();

        var done = broker.getCloudletFinishedList();
        double makespan = done.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        done.stream().max(Comparator.comparingDouble(Cloudlet::getFinishTime)).ifPresent(c -> System.out.printf("TERAKHIR: cloudlet %d | panjang=%d MI | start=%.2f | selesai=%.2f | VM=%d%n", c.getId(), c.getLength(), c.getStartTime(), c.getFinishTime(), c.getVm().getId()));
        done.stream().filter(c -> c.getStartTime() > 200).forEach(c -> System.out.printf("TELAT: cloudlet %d | start=%.2f | VM=%d | host=%d%n", c.getId(), c.getStartTime(), c.getVm().getId(), c.getVm().getHost().getId()));
        double avgResp = done.stream().mapToDouble(Cloudlet::getFinishTime).average().orElse(0);
        double avgWait = done.stream().mapToDouble(Cloudlet::getStartTime).average().orElse(0);

        System.out.printf("%n=== Kebijakan: %s ===%n", KEBIJAKAN.toUpperCase());
        System.out.printf("Cloudlet selesai      : %d%n", done.size());
        System.out.printf("Makespan              : %.2f detik%n", makespan);
        System.out.printf("Rata-rata response    : %.2f detik%n", avgResp);
        System.out.printf("Rata-rata waktu tunggu: %.2f detik%n", avgWait);
    }

    // Selama ada VM kosong dan task menunggu: pilih task, pilih VM tercepat, kirim
    static void dispatch() {
        while (!vmBebas.isEmpty() && !menunggu.isEmpty()) {
            Vm vm = vmBebas.stream().max(Comparator.comparingDouble(Vm::getMips)).get();
            Cloudlet c = pilih();
            double rr = responseRatio(c);
            menunggu.removeIf(x -> x == c);
            vmBebas.removeIf(x -> x == vm);
            System.out.printf("DISPATCH t=%.2f | cloudlet %d (%d MI, RR=%.2f) -> VM %d (%.0f MIPS)%n",
                    sim.clock(), c.getId(), c.getLength(), rr, vm.getId(), vm.getMips());
            c.setVm(vm);
            c.addOnFinishListener(info -> {
                vmBebas.add(vm);
                dispatch();
            });
            broker.submitCloudlet(c);
        }
    }

    static Cloudlet pilih() {
        if (KEBIJAKAN.equals("fcfs")) return menunggu.get(0);
        Cloudlet terbaik = menunggu.get(0);
        double rrTerbaik = -1;
        for (Cloudlet c : menunggu) {
            double rr = responseRatio(c);
            if (rr > rrTerbaik) { rrTerbaik = rr; terbaik = c; }
        }
        return terbaik;
    }

    // RR = (waktu tunggu + service time) / service time; arrival semua task = 0
    static double responseRatio(Cloudlet c) {
        double tunggu = sim.clock();
        double service = c.getLength() / mipsAcuan;
        return (tunggu + service) / service;
    }
}