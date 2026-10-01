# Simulasi Penjadwalan Task Cloud dengan Highest Response Ratio Next (HRRN)

**Mata kuliah:** Strategi Optimasi Komputasi Awan
**Kelompok:** 3
**Anggota:**

| Nama | NRP |
|---|---|
| Zein Muhammad Hasan | 5027241035 |
| Ahmad Rafi Fadhillah Dwiputra | 5027241068 |
| Naruna Vicranthyo Putra Gangga | 5027241105 |
| Paundra Pujo Darmawan | 5027241008 |
| Hansen Chang | 5027241028 |

**Tools:** CloudSim Plus 8.5.2, Java 21, Maven
**Waktu:** Oktober 2026

---

## 1. Pendahuluan

### 1.1 Latar belakang

Penjadwalan task (*task scheduling*) menentukan task mana yang dijalankan lebih dulu dan di mesin virtual (VM) mana. Keputusan ini memengaruhi waktu tunggu, waktu respons, pemakaian resource, dan konsumsi energi datacenter. Pada proyek ini kami mensimulasikan penjadwalan task di lingkungan cloud heterogen menggunakan algoritma **Highest Response Ratio Next (HRRN)** dan membandingkannya dengan dua algoritma pembanding, **FCFS** dan **SJF**.

### 1.2 Tujuan

1. Membangun simulasi cloud heterogen (2 datacenter, 10 host, 20 VM) di CloudSim Plus sesuai draf desain kelompok.
2. Mengimplementasikan scheduler HRRN dan memverifikasi kebenaran logikanya.
3. Membandingkan HRRN dengan FCFS dan SJF pada beberapa tingkat beban kerja.
4. Mengukur objective pada draf desain, yaitu **makespan** dan **energy consumption**, beserta metrik pendukung.

### 1.3 Ruang lingkup dan perbedaan dengan draf desain

| Item di draf | Status di simulasi ini |
|---|---|
| Mixed workload (small/medium/large) | Dipakai, dibangkitkan secara sintetis |
| Dataset cloud dari luar | **Belum dipakai** (lihat bagian 8) |
| 2 datacenter, 10 host heterogen, 20 VM | Dipakai |
| 100-500 cloudlet | Dipakai sebagai 60, 200, dan 400 cloudlet |
| Arrival time, deadline, ukuran input/output | Dipakai. Deadline adalah asumsi kami |
| Priority | **Tidak dipakai** (HRRN tidak memakai priority) |
| Dependency antar task | **Belum diimplementasikan** |
| Objective: makespan dan energy | Dipakai |
| Metrik: makespan, energy, response time, utilization, throughput, load imbalance | Dipakai. Average execution time tidak dilaporkan terpisah |

---

## 2. Landasan Teori

### 2.1 HRRN

HRRN adalah algoritma penjadwalan **non-preemptive**. Setiap kali ada resource yang kosong, HRRN memilih task dari antrean dengan *response ratio* (RR) tertinggi:

```
RR = (waktu tunggu + waktu layanan) / waktu layanan
```

Akibatnya, task pendek mendapat RR tinggi sejak awal sehingga cepat dijalankan, sedangkan task panjang yang lama menunggu RR-nya terus naik sehingga tidak menunggu tanpa batas (*starvation*).

Contoh verifikasi dengan 5 task (arrival, service): P1 (0, 3), P2 (2, 6), P3 (4, 4), P4 (6, 5), P5 (8, 2). Urutan hasil HRRN adalah P1, P2, P3, P5, P4 dengan waktu selesai 3, 9, 13, 15, 20. Pada detik 13, P5 (RR 3,50) didahulukan dari P4 (RR 2,80 saat dipilih di detik 15) walaupun P4 datang lebih awal. Implementasi kami menghasilkan urutan dan waktu yang sama persis dengan perhitungan manual ini.

### 2.2 Algoritma pembanding

- **FCFS (First Come First Served):** task dengan waktu datang paling awal dijalankan lebih dulu.
- **SJF (Shortest Job First):** dari task yang sudah datang, dipilih yang paling pendek. Non-preemptive.

---

## 3. Desain Sistem

### 3.1 Infrastruktur

| Datacenter | Host |
|---|---|
| DC 1 | 2 Small (4 core/16 GB), 2 Medium (8 core/32 GB), 1 Large (16 core/64 GB) |
| DC 2 | 1 Small (4 core/16 GB), 2 Medium (8 core/32 GB), 2 Large (16 core/64 GB) |

Total 92 core dan 368 GB RAM. Setiap core host berkecepatan 2000 MIPS.

| Tipe VM | Jumlah | vCPU | RAM | Kecepatan per vCPU |
|---|---|---|---|---|
| Small | 8 | 2 | 4 GB | 1000 MIPS |
| Medium | 8 | 4 | 8 GB | 1500 MIPS |
| Large | 4 | 8 | 16 GB | 2000 MIPS |

Total 20 VM (80 vCPU). Kecepatan per VM dibuat berbeda agar waktu eksekusi sebuah task bergantung pada VM yang dipakai. Penempatan VM ke host memakai kebijakan bawaan CloudSim Plus (`VmAllocationPolicySimple`). Dalam praktiknya hanya 6 VM yang masuk DC 1 dan 14 VM masuk DC 2, karena broker bawaan mengisi DC 1 lebih dulu.

Kecepatan VM (1000/1500/2000 MIPS) dan komposisi host per datacenter adalah asumsi kami, bukan angka dari draf.

### 3.2 Workload

Semua workload dibangkitkan secara sintetis dan dapat diulang (seed tetap).

| Kategori | Proporsi | Panjang task |
|---|---|---|
| Kecil | 50% | 5.000-20.000 MI |
| Sedang | 30% | 20.000-60.000 MI |
| Besar | 20% | 60.000-150.000 MI |

- Setiap cloudlet memakai 1 core, ukuran file input dan output 300 byte.
- **Panjang task** selalu memakai seed 42. Jadi tiap jumlah cloudlet memakai deret panjang yang sama.
- **Arrival time** acak seragam pada detik 0 sampai 60. Seed arrival divariasikan (seed 1-5 untuk percobaan utama, ditambah seed 7 pada n = 60).
- **Deadline** = `arrival + f × service time`, dengan `f` acak 3 sampai 8 dan service time = `panjang / 1400 MIPS`. Ini asumsi kami.

### 3.3 Scheduler

Aturan umum untuk ketiga kebijakan:

1. Satu VM hanya menjalankan **satu cloudlet dalam satu waktu** dan non-preemptive.
2. Saat ada VM kosong dan ada task yang sudah datang, scheduler memilih satu task sesuai kebijakan, memilih VM, lalu mengirimkannya.
3. Saat sebuah cloudlet selesai, VM-nya ditandai kosong dan proses pemilihan diulang.

Pemilihan task:

| Kebijakan | Aturan |
|---|---|
| FCFS | Arrival paling awal |
| SJF | Panjang task paling kecil |
| HRRN | RR tertinggi. Waktu layanan untuk RR diestimasi sebagai `panjang / 1400 MIPS`, yaitu rata-rata kecepatan semua VM |

Estimasi waktu layanan memakai rata-rata kecepatan VM supaya urutan task tidak bergantung pada VM mana yang kebetulan sedang kosong.

Pemilihan VM (dua aturan, dipakai pada bagian 6.5):

| Aturan | Keterangan |
|---|---|
| `fast` | VM kosong dengan MIPS tertinggi (aturan utama di seluruh percobaan kecuali bagian 6.5) |
| `green` | VM kosong dengan energi dinamis per MI terendah, dihitung dari jenis host tempat VM berada |

### 3.4 Mekanisme kedatangan bertahap

Agar scheduler "bangun" saat task baru datang, tiap task memiliki satu **cloudlet pemicu** berukuran 1 MI yang dikirim pada detik kedatangan task. Saat pemicu selesai, scheduler dijalankan. Cloudlet pemicu **tidak ikut dihitung** dalam semua metrik.

---

## 4. Metrik dan Model Energi

| Metrik | Definisi |
|---|---|
| Makespan | Waktu selesai cloudlet terakhir |
| Response time | `waktu selesai − arrival`, dirata-rata |
| Waktu tunggu | `waktu mulai − arrival`, dirata-rata |
| Waktu tunggu terlama | Nilai maksimum dari waktu tunggu (indikator starvation) |
| Throughput | `jumlah task / makespan` |
| Utilisasi CPU | `total waktu sibuk semua VM / (20 × makespan)` |
| Load imbalance | Simpangan baku waktu sibuk per VM dibagi rata-ratanya (makin kecil makin seimbang) |
| Task lewat deadline | Jumlah task dengan waktu selesai melewati deadline |
| Energi | Lihat model di bawah |

Waktu mulai dan selesai dari simulator sudah mengandung jeda sekitar 0,1-0,2 detik (overhead bawaan simulator). Jeda ini berlaku sama untuk semua kebijakan.

**Model energi (asumsi kelompok).** Host menyala terus dari detik 0 sampai makespan. Untuk host dengan `k` core:

- daya idle = `40 + 5k` watt,
- daya maksimum = `100 + 10k` watt,
- daya bertambah linear terhadap jumlah core yang sibuk, sehingga daya dinamis per core = `(60 + 5k) / k` watt.

Energi total = (jumlah daya idle semua host × makespan) + (daya dinamis per core × waktu sibuk tiap cloudlet). Jumlah daya idle seluruh host adalah 860 W. Angka energi dilaporkan dalam Wh.

---

## 5. Implementasi dan Verifikasi

### 5.1 Tahapan pengerjaan

| Tahap | Isi | Hasil |
|---|---|---|
| 0 | Setup Java, Maven, CloudSim Plus | Simulasi uji 1 cloudlet berhasil (10.000 MI di 1000 MIPS selesai dalam 10 detik) |
| 1 | Infrastruktur 2 DC, 10 host, 20 VM | Seluruh 20 VM terbentuk, tidak ada yang gagal |
| 2 | 20 cloudlet dengan scheduler bawaan | Semua selesai, makespan 69,75 s |
| 3 | Uji logika HRRN di luar CloudSim | Cocok dengan perhitungan manual |
| 4 | HRRN, FCFS, SJF di dalam CloudSim dengan arrival bertahap dan metrik lengkap | Bagian 6 |
| 5 | Model energi dan aturan pemilihan VM | Bagian 6.4 dan 6.5 |

### 5.2 Kendala teknis dan pelajaran

1. **Hanya 20 cloudlet yang selesai.** Saat cloudlet lebih banyak dari VM dengan `UtilizationModelFull`, jumlah cloudlet yang selesai berhenti tepat di 20 (jumlah VM). Setelah kebutuhan RAM dan bandwidth cloudlet dikecilkan (5%), seluruh 60 cloudlet selesai. Kesimpulan kami: kebutuhan RAM 100% per cloudlet membatasi VM hanya menjalankan satu cloudlet sekaligus. Pengaturan ini dipakai di semua percobaan akhir.
2. **Cloudlet telat 1000 detik.** Empat belas cloudlet yang berjalan di VM 6-19 (VM yang dibuat di DC 2 pada detik 0,10) baru mulai pada detik 1000,10. Waktu mulainya ikut bergeser saat delay penghancuran VM diubah (500,10 dan 2000,10), sehingga terbukti terkait dengan delay itu. Mekanisme pastinya tidak kami selidiki. Solusinya: pengiriman cloudlet baru dimulai setelah seluruh VM berhasil dibuat (`addOnVmsCreatedListener`).
3. **Task terkirim ganda.** Penghapusan task dari antrean awalnya memakai `remove(objek)`, yang menyebabkan beberapa task terkirim dua kali dan jumlah cloudlet selesai menjadi 41 dari 60. Perbaikannya menghapus berdasarkan identitas objek (`removeIf(x -> x == c)`).
4. **Delay penghancuran VM.** Delay penghancuran VM diatur panjang (1000 detik) agar VM tidak dihancurkan sementara task berikutnya masih datang. Nilai ini tidak memengaruhi metrik karena metrik dihitung dari waktu selesai cloudlet.

---

## 6. Hasil Eksperimen

Semua baris di bawah telah diverifikasi bahwa seluruh cloudlet selesai (jumlah selesai sama dengan jumlah yang dikirim).

### 6.1 Skenario A: semua task datang bersamaan (60 task, detik 0)

| | FCFS | HRRN |
|---|---|---|
| Makespan | 182,06 s | 191,55 s |
| Rata-rata response | 46,08 s | 40,09 s |
| Rata-rata waktu tunggu | 17,79 s | 11,94 s |

Pada skenario ini waktu tunggu semua task sama saat t = 0, sehingga HRRN berperilaku mirip SJF (task pendek didahulukan). Sifat khas HRRN baru terlihat saat arrival bertahap.

### 6.2 Skenario B: arrival bertahap, n = 60 (rata-rata 6 seed)

| Metrik | FCFS | SJF | HRRN |
|---|---|---|---|
| Makespan (s) | 160,2 | 166,6 | 159,1 |
| Response (s) | 31,85 | 30,32 | 30,47 |
| Waktu tunggu (s) | 4,47 | 3,07 | 3,33 |
| Throughput (task/s) | 0,381 | 0,368 | 0,380 |
| Utilisasi CPU (%) | 52,2 | 50,1 | 51,6 |
| Load imbalance | 0,28 | 0,31 | 0,30 |
| Task lewat deadline (total dari 6 seed) | 1 | 0 | 0 |

Pada beban ringan, sistem belum padat (utilisasi sekitar 50%), sehingga antrean pendek dan perbedaan antar kebijakan kecil.

### 6.3 Skenario C: beban berat (rata-rata 5 seed)

| n | Metrik | FCFS | SJF | HRRN |
|---|---|---|---|---|
| 200 | Waktu tunggu (s) | 89,6 | 43,3 | 45,8 |
| 200 | Response (s) | 117,5 | 71,2 | 74,0 |
| 200 | Task lewat deadline (rata-rata per seed) | 86,4 | 4,4 | 2,6 |
| 200 | Makespan (s) | 345,4 | 356,0 | 354,4 |
| 400 | Waktu tunggu (s) | 240,3 | 117,5 | 120,1 |
| 400 | Response (s) | 269,6 | 146,8 | 149,5 |
| 400 | Task lewat deadline (rata-rata per seed) | 279,4 | 120,0 | 127,8 |
| 400 | Makespan (s) | 666,8 | 664,1 | 670,3 |

### 6.4 Waktu tunggu terlama dan energi (n = 200, rata-rata 5 seed)

| | FCFS | SJF | HRRN |
|---|---|---|---|
| Waktu tunggu terlama, rata-rata (s) | 198,0 | 211,3 | 190,5 |
| Waktu tunggu terlama, terburuk dari 5 seed (s) | 201,9 | 218,4 | 195,2 |
| Energi rata-rata (Wh) | 100,5 | 102,9 | 102,7 |

### 6.5 Aturan pemilihan VM (HRRN, n = 200, rata-rata 5 seed)

| Aturan VM | Makespan (s) | Energi (Wh) |
|---|---|---|
| `fast` | 354,4 | 102,7 |
| `green` | 347,6 | 101,1 |

---

## 7. Analisis

**1. Beban berat: HRRN dan SJF jauh lebih baik dari FCFS.** Pada n = 200 dan n = 400, HRRN menurunkan waktu tunggu rata-rata sekitar 49-50% dan response rata-rata sekitar 37-45% dibanding FCFS. Jumlah task yang melewati deadline turun drastis (n = 200: dari 86,4 menjadi 2,6). Hasil ini konsisten dengan prinsip bahwa mendahulukan task pendek menurunkan rata-rata waktu tunggu.

**2. Beban ringan: perbedaan kecil.** Pada n = 60, sistem hanya terpakai sekitar 50% sehingga jarang ada antrean. Selisih response antar kebijakan sekitar 1,5 detik dari sekitar 31 detik, dan selisih ini tidak cukup kuat untuk menyimpulkan kebijakan mana yang lebih baik.

**3. HRRN dan SJF hampir sama pada rata-rata.** SJF sedikit lebih rendah pada waktu tunggu rata-rata (sekitar 5% pada n = 200 dan sekitar 2% pada n = 400). Pada jumlah task lewat deadline urutannya berubah: HRRN lebih baik pada n = 200 (2,6 vs 4,4) tetapi lebih buruk pada n = 400 (127,8 vs 120,0). Karena tidak konsisten, kami tidak menyimpulkan salah satunya lebih unggul di metrik rata-rata.

**4. Keunggulan HRRN terlihat pada waktu tunggu terlama.** Pada n = 200, waktu tunggu terlama HRRN (190,5 s) sekitar 10% lebih rendah dari SJF (211,3 s) dan sekitar 4% lebih rendah dari FCFS (198,0 s). Pola ini sejalan dengan tujuan HRRN mencegah task panjang menunggu terlalu lama, sementara SJF bisa menunda task panjang paling lama. Mengingat hanya 5 seed dan selisihnya beberapa persen, hasil ini kami tulis sebagai **kecenderungan**, bukan bukti pasti.

**5. Makespan tidak punya pemenang yang konsisten.** Selisih makespan antar kebijakan di bawah sekitar 5% pada n = 60, sekitar 3% pada n = 200, dan sekitar 1% pada n = 400, dan urutan terbaiknya berganti antar kondisi. Pada beberapa seed ketiga kebijakan bahkan menghasilkan makespan identik, yang menunjukkan makespan ditentukan oleh satu task panjang, bukan oleh urutan antrean. Pada skenario A, makespan HRRN sempat lebih tinggi dari FCFS, tetapi pada percobaan dengan banyak seed pola itu tidak konsisten.

**6. Makespan dan energi tidak bertentangan pada model ini.** Draf desain memperkirakan makespan dan energi bisa bertentangan. Pada simulasi ini energi mengikuti makespan: energi FCFS (100,5 Wh) sedikit lebih rendah karena makespan-nya pada n = 200 juga terendah. Penyebabnya adalah asumsi model energi: host menyala terus, dan daya idle (860 W untuk seluruh host) jauh lebih besar daripada daya tambahan saat task berjalan. Akibatnya energi kira-kira sebanding dengan makespan. Aturan VM `green` memberi hasil sedikit lebih baik pada kedua metrik (makespan 347,6 vs 354,4 s; energi 101,1 vs 102,7 Wh, selisih sekitar 2%). Penyebab makespan ikut turun tidak kami selidiki, dan selisih sekecil ini dari 5 seed belum cukup untuk menyatakan `green` lebih baik. Untuk memunculkan trade-off makespan vs energi, model perlu mampu mematikan host yang menganggur, dan hal itu belum dilakukan.

---

## 8. Batasan

1. **Dataset sintetis.** Workload dibangkitkan sendiri, belum memakai dataset cloud publik (misalnya GoCJ) seperti yang diusulkan draf.
2. **Variasi terbatas.** Panjang task selalu memakai seed yang sama (42); hanya arrival time yang divariasikan, dan hanya 5-6 seed. Selisih kecil (beberapa persen) tidak dapat dianggap signifikan secara statistik, dan tidak dilakukan uji statistik.
3. **Asumsi kelompok.** Kecepatan VM, komposisi host, rumus deadline, dan model energi adalah asumsi, bukan data nyata.
4. **Fitur draf yang belum ada.** Dependency antar task belum diimplementasikan, priority tidak dipakai, dan average execution time tidak dilaporkan terpisah.
5. **Distribusi VM antar datacenter timpang** (6 VM di DC 1, 14 VM di DC 2) karena perilaku broker bawaan.
6. **Mekanisme beberapa kendala teknis** (bagian 5.2, poin 1 dan 2) dibuktikan lewat percobaan perilaku, tetapi tidak diselidiki sampai ke kode internal CloudSim Plus.

---

## 9. Kesimpulan

1. Simulasi cloud heterogen (2 datacenter, 10 host, 20 VM) dan scheduler HRRN berhasil dibangun di CloudSim Plus, dan logika HRRN terverifikasi dengan perhitungan manual.
2. Pada beban berat (200 dan 400 task), HRRN dan SJF menurunkan waktu tunggu dan response sekitar separuh dibanding FCFS, dan jumlah task yang melewati deadline turun drastis.
3. HRRN dan SJF hampir sama pada metrik rata-rata. HRRN cenderung memiliki waktu tunggu terlama yang lebih rendah (sekitar 10% di bawah SJF pada n = 200), sesuai tujuan pencegahan starvation.
4. Makespan tidak dibedakan secara konsisten oleh kebijakan antrean, dan energi mengikuti makespan pada model energi yang dipakai.

**Saran pengembangan:** memakai dataset publik dengan variasi panjang task, menambah jumlah seed dan uji statistik, menambahkan dependency antar task, serta memperluas model energi dengan mematikan host menganggur agar trade-off makespan vs energi dapat diamati.

---

## Lampiran A. Cara menjalankan

Folder proyek memakai Maven dengan dependensi `org.cloudsimplus:cloudsimplus:8.5.2`. File utama simulasi adalah `Tahap4.java` (dengan `Tahap1.java` sebagai pembuat host dan VM).

```bash
# satu kebijakan (fcfs | sjf | hrrn), jumlah task n, seed arrival
mvn -q compile exec:java -Dexec.mainClass=org.example.Tahap4 \
    -Dpolicy=hrrn -Dn=200 -Dseed=1 | grep HASIL

# aturan pemilihan VM (fast | green) dan baris energi
mvn -q compile exec:java -Dexec.mainClass=org.example.Tahap4 \
    -Dpolicy=hrrn -DvmRule=green -Dn=200 -Dseed=1 | grep EKSTRA
```

Format keluaran `HASIL`: jumlah selesai, makespan, response, waktu tunggu, throughput, utilisasi, load imbalance, dan jumlah task lewat deadline. Format keluaran `EKSTRA`: makespan, waktu tunggu terlama, dan energi (Wh).

## Lampiran B. Cuplikan kode inti

Response ratio dan pemilihan task:

```java
// RR = (waktu tunggu + service time) / service time
static double responseRatio(Cloudlet c) {
    double tunggu = sim.clock() - arrival.get(c);
    double service = c.getLength() / mipsAcuan;   // mipsAcuan = rata-rata MIPS semua VM (1400)
    return (tunggu + service) / service;
}

static Cloudlet pilih(List<Cloudlet> siap) {
    switch (KEBIJAKAN) {
        case "fcfs": return siap.stream().min(Comparator.comparingDouble(arrival::get)).get();
        case "sjf":  return siap.stream().min(Comparator.comparingLong(Cloudlet::getLength)).get();
        default:     return siap.stream().max(Comparator.comparingDouble(Tahap4::responseRatio)).get();
    }
}
```

Pengiriman task ke VM kosong, dan pengulangan saat task selesai:

```java
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
        c.addOnFinishListener(info -> { vmBebas.add(vm); dispatch(); });
        broker.submitCloudlet(c);
    }
}
```

Pengaturan cloudlet yang penting (RAM dan bandwidth dikecilkan agar satu VM tidak terkunci oleh satu cloudlet):

```java
var c = new CloudletSimple(length, 1, new UtilizationModelFull());
c.setUtilizationModelRam(new UtilizationModelDynamic(0.05));
c.setUtilizationModelBw(new UtilizationModelDynamic(0.05));
```
