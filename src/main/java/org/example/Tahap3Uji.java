package org.example;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class Tahap3Uji {
    public static void main(String[] args) {
        var sim = new CloudSimPlus();
        new DatacenterSimple(sim, List.of(Tahap1.createHost(4, 16)));

        var broker = new DatacenterBrokerSimple(sim);
        broker.setVmDestructionDelayFunction(v -> 1000.0);

        Vm vm = Tahap1.createVm(1000, 2, 4);
        broker.submitVm(vm);

        // 3 cloudlet, masing-masing 10.000 MI (10 detik di VM 1000 MIPS)
        Deque<Cloudlet> antrean = new ArrayDeque<>();
        for (int i = 0; i < 3; i++) {
            antrean.add(new CloudletSimple(10_000, 1, new UtilizationModelFull()));
        }

        kirimBerikutnya(broker, vm, antrean);
        sim.start();

        var selesai = broker.getCloudletFinishedList();
        System.out.printf("UJI: selesai %d dari 3%n", selesai.size());
        selesai.forEach(c -> System.out.printf("UJI: cloudlet %d selesai di detik %.2f%n",
                c.getId(), c.getFinishTime()));
    }

    // Kirim satu cloudlet; saat selesai, kirim yang berikutnya
    static void kirimBerikutnya(DatacenterBroker broker, Vm vm, Deque<Cloudlet> antrean) {
        Cloudlet c = antrean.poll();
        if (c == null) return;
        c.setVm(vm);
        c.addOnFinishListener(info -> kirimBerikutnya(broker, vm, antrean));
        broker.submitCloudlet(c);
    }
}