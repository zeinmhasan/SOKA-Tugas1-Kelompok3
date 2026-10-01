package org.example;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Tahap2 {
    public static void main(String[] args) {
        var sim = new CloudSimPlus();

        // Infrastruktur sama seperti Tahap 1
        new DatacenterSimple(sim, List.of(
                Tahap1.createHost(4, 16), Tahap1.createHost(4, 16),
                Tahap1.createHost(8, 32), Tahap1.createHost(8, 32),
                Tahap1.createHost(16, 64)));
        new DatacenterSimple(sim, List.of(
                Tahap1.createHost(4, 16),
                Tahap1.createHost(8, 32), Tahap1.createHost(8, 32),
                Tahap1.createHost(16, 64), Tahap1.createHost(16, 64)));

        var broker = new DatacenterBrokerSimple(sim);

        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < 4; i++) vms.add(Tahap1.createVm(2000, 8, 16));
        for (int i = 0; i < 8; i++) vms.add(Tahap1.createVm(1500, 4, 8));
        for (int i = 0; i < 8; i++) vms.add(Tahap1.createVm(1000, 2, 4));

        // 20 cloudlet: campuran kecil / sedang / besar (seed tetap)
        var rnd = new Random(42);
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long length;
            int r = rnd.nextInt(10);
            if (r < 5)      length = 5_000 + rnd.nextInt(15_000);   // kecil
            else if (r < 8) length = 20_000 + rnd.nextInt(40_000);  // sedang
            else            length = 60_000 + rnd.nextInt(90_000);  // besar
            var c = new CloudletSimple(length, 1, new UtilizationModelFull());
            c.setFileSize(300).setOutputSize(300);
            cloudlets.add(c);
        }

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        sim.start();

        var finished = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(finished).build();

        double makespan = finished.stream()
                .mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        System.out.printf("%nCloudlet selesai: %d | Makespan: %.2f detik%n",
                finished.size(), makespan);
    }
}