package org.example;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Tahap3a {
    static final int JUMLAH_CLOUDLET = Integer.getInteger("n", 60);

    public static void main(String[] args) {
        var sim = new CloudSimPlus();

        new DatacenterSimple(sim, List.of(
                Tahap1.createHost(4, 16), Tahap1.createHost(4, 16),
                Tahap1.createHost(8, 32), Tahap1.createHost(8, 32),
                Tahap1.createHost(16, 64))).setSchedulingInterval(1);
        new DatacenterSimple(sim, List.of(
                Tahap1.createHost(4, 16),
                Tahap1.createHost(8, 32), Tahap1.createHost(8, 32),
                Tahap1.createHost(16, 64), Tahap1.createHost(16, 64))).setSchedulingInterval(1);

        var broker = new DatacenterBrokerSimple(sim);
        broker.setVmDestructionDelayFunction(vm -> 1000.0);

        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < 4; i++) vms.add(Tahap1.createVm(2000, 8, 16));
        for (int i = 0; i < 8; i++) vms.add(Tahap1.createVm(1500, 4, 8));
        for (int i = 0; i < 8; i++) vms.add(Tahap1.createVm(1000, 2, 4));
        // satu task per satu waktu di tiap VM
        for (Vm vm : vms) vm.setCloudletScheduler(new CloudletSchedulerTimeShared());

        var rndLen = new Random(42);  // panjang task (sama dengan Tahap 2)
        var rndArr = new Random(7);   // arrival time
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < JUMLAH_CLOUDLET; i++) {
            long length;
            int r = rndLen.nextInt(10);
            if (r < 5)      length = 5_000 + rndLen.nextInt(15_000);
            else if (r < 8) length = 20_000 + rndLen.nextInt(40_000);
            else            length = 60_000 + rndLen.nextInt(90_000);
            var c = new CloudletSimple(length, 1, new UtilizationModelFull());
            c.setFileSize(300).setOutputSize(300);
            c.setUtilizationModelRam(new UtilizationModelDynamic(0.05));
            c.setUtilizationModelBw(new UtilizationModelDynamic(0.05));
            c.setSubmissionDelay(Boolean.getBoolean("tanpaDelay") ? 0 : rndArr.nextInt(600) / 10.0);  // datang di detik 0-60
            cloudlets.add(c);
        }

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        sim.start();

        var done = broker.getCloudletFinishedList();
        System.out.printf("PROGRES: clock=%.2f%n", sim.clock());
        broker.getCloudletSubmittedList().stream().filter(c -> !c.isFinished()).limit(6).forEach(c -> System.out.printf("PROGRES: cloudlet %d | start=%.1f | selesai=%d dari %d MI%n", c.getId(), c.getStartTime(), c.getFinishedLengthSoFar(), c.getLength()));
        broker.getCloudletSubmittedList().stream().filter(c -> !c.isFinished()).forEach(c -> System.out.printf("BELUM SELESAI: cloudlet %d | status=%s | VM=%d | delay=%.1f%n", c.getId(), c.getStatus(), c.getVm().getId(), c.getSubmissionDelay()));
        double makespan = done.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        double avgResp = done.stream()
                .mapToDouble(c -> c.getFinishTime() - c.getSubmissionDelay()).average().orElse(0);
        double avgWait = done.stream()
                .mapToDouble(c -> c.getStartTime() - c.getSubmissionDelay()).average().orElse(0);

        System.out.printf("%n=== Baseline (broker bawaan, VM space-shared) ===%n");
        System.out.printf("Cloudlet selesai     : %d%n", done.size());
        System.out.printf("Makespan             : %.2f detik%n", makespan);
        System.out.printf("Rata-rata response   : %.2f detik%n", avgResp);
        System.out.printf("Rata-rata waktu tunggu: %.2f detik%n", avgWait);
    }
}