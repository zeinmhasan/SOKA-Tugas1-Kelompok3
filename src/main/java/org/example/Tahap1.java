package org.example;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

public class Tahap1 {
    static final double HOST_PE_MIPS = 2000;

    public static void main(String[] args) {
        var sim = new CloudSimPlus();

        // DC 1: 2 Small, 2 Medium, 1 Large
        var dc1 = new DatacenterSimple(sim, List.of(
                createHost(4, 16), createHost(4, 16),
                createHost(8, 32), createHost(8, 32),
                createHost(16, 64)));

        // DC 2: 1 Small, 2 Medium, 2 Large
        var dc2 = new DatacenterSimple(sim, List.of(
                createHost(4, 16),
                createHost(8, 32), createHost(8, 32),
                createHost(16, 64), createHost(16, 64)));

        var broker = new DatacenterBrokerSimple(sim);

        // Large dulu supaya penempatan tidak terfragmentasi
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < 4; i++) vms.add(createVm(2000, 8, 16));
        for (int i = 0; i < 8; i++) vms.add(createVm(1500, 4, 8));
        for (int i = 0; i < 8; i++) vms.add(createVm(1000, 2, 4));

        // Cetak setiap VM saat berhasil ditempatkan di host
        for (Vm vm : vms) {
            vm.addOnHostAllocationListener(info ->
                    System.out.printf("VM %2d (%d vCPU, %.0f MIPS) -> DC %d, Host %d (%d core)%n",
                            info.getVm().getId(),
                            info.getVm().getPesNumber(),
                            info.getVm().getMips(),
                            info.getHost().getDatacenter().getId(),
                            info.getHost().getId(),
                            info.getHost().getPesNumber()));
        }

        broker.submitVmList(vms);
        sim.start();

        System.out.println("VM gagal dibuat: " + broker.getVmFailedList().size());
    }

    static Host createHost(int cores, int ramGb) {
        List<Pe> pes = new ArrayList<>();
        for (int i = 0; i < cores; i++) pes.add(new PeSimple(HOST_PE_MIPS));
        return new HostSimple(ramGb * 1024L, 10_000, 1_000_000, pes);
    }

    static Vm createVm(double mips, int vcpu, int ramGb) {
        var vm = new VmSimple(mips, vcpu);
        vm.setRam(ramGb * 1024L).setBw(1000).setSize(10_000);
        return vm;
    }
}