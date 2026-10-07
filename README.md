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
3. Membandingkan HRRN dengan FCFS dan SJF pada lima ukuran workload (60, 200, 1.000, 5.000, dan 10.000 cloudlet), masing-masing 3 kali simulasi per algoritma lalu dirata-rata, untuk melihat apakah hasil pada beban ringan dan beban berat sama.
4. Mengukur objective pada draf desain, yaitu **makespan** dan **energy consumption**, beserta metrik pendukung.

### 1.3 Ruang lingkup dan perbedaan dengan draf desain

| Item di draf | Status di simulasi ini |
|---|---|
| Mixed workload (small/medium/large) | Dipakai, dibangkitkan secara sintetis |
| Dataset cloud dari luar | **Belum dipakai** (lihat bagian 8) |
| 2 datacenter, 10 host heterogen, 20 VM | Dipakai |
| 100-500 cloudlet | Diperluas (revisi) menjadi 60, 200, 1.000, 5.000, dan 10.000 cloudlet |
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
- **Arrival time** acak seragam pada detik 0 sampai 60, untuk semua ukuran workload. Karena jendela kedatangan tetap, menambah jumlah cloudlet berarti menambah kepadatan beban.
- **Pengulangan:** setiap kombinasi ukuran workload dan algoritma disimulasikan 3 kali dengan seed arrival 1, 2, dan 3, lalu hasilnya dirata-rata. Simulasi bersifat deterministik, sehingga pengulangan dengan seed yang sama akan memberi angka yang sama persis; karena itu yang divariasikan adalah seed arrival.
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
| 5 | Model energi dan aturan pemilihan VM | Bagian 6.3 dan 6.5 |
| 6 | Revisi: 5 ukuran workload × 3 algoritma × 3 simulasi dalam satu program (`Tahap5.java`) | Bagian 6.2 sampai 6.4 |

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

### 6.2 Skenario B: arrival bertahap, waktu tunggu dan response (rata-rata 3 simulasi)

Bagian 6.2 sampai 6.4 adalah hasil eksperimen revisi: 5 ukuran workload × 3 algoritma × 3 simulasi (seed arrival 1-3) = 45 simulasi. Angka mentah tiap simulasi dan rata-ratanya ada di `hasil_revisi.txt`.

| n | Metrik | FCFS | SJF | HRRN |
|---|---|---|---|---|
| 60 | Waktu tunggu (s) | 4,01 | 2,92 | 3,00 |
| 60 | Response (s) | 31,54 | 30,24 | 30,07 |
| 200 | Waktu tunggu (s) | 90,21 | 41,03 | 43,78 |
| 200 | Response (s) | 117,97 | 68,94 | 72,01 |
| 1.000 | Waktu tunggu (s) | 699,07 | 353,47 | 355,52 |
| 1.000 | Response (s) | 728,97 | 383,41 | 385,47 |
| 5.000 | Waktu tunggu (s) | 3.510,81 | 1.814,99 | 1.822,97 |
| 5.000 | Response (s) | 3.539,53 | 1.843,70 | 1.851,69 |
| 10.000 | Waktu tunggu (s) | 7.036,87 | 3.631,99 | 3.637,57 |
| 10.000 | Response (s) | 7.065,30 | 3.660,42 | 3.666,00 |

Perubahan relatif HRRN, dihitung dari tabel di atas (nilai negatif berarti HRRN lebih rendah):

| n | Waktu tunggu HRRN vs FCFS | Response HRRN vs FCFS | Waktu tunggu HRRN vs SJF | Task lewat deadline (FCFS / SJF / HRRN) |
|---|---|---|---|---|
| 60 | −25,2% | −4,7% | +2,7% | 0,0% / 0,0% / 0,0% |
| 200 | −51,5% | −39,0% | +6,7% | 44,0% / 1,5% / 1,0% |
| 1.000 | −49,1% | −47,1% | +0,6% | 88,5% / 82,1% / 88,3% |
| 5.000 | −48,1% | −47,7% | +0,4% | 97,8% / 95,3% / 98,3% |
| 10.000 | −48,3% | −48,1% | +0,2% | 98,9% / 97,2% / 99,1% |

### 6.3 Makespan, throughput, utilisasi, dan energi (rata-rata 3 simulasi)

| n | Metrik | FCFS | SJF | HRRN |
|---|---|---|---|---|
| 60 | Makespan (s) | 155,8 | 177,9 | 162,9 |
| 60 | Throughput (task/s) | 0,388 | 0,341 | 0,368 |
| 60 | Utilisasi CPU (%) | 53,4 | 46,5 | 49,9 |
| 60 | Load imbalance | 0,28 | 0,32 | 0,31 |
| 60 | Energi (Wh) | 42,5 | 47,7 | 44,0 |
| 200 | Makespan (s) | 329,0 | 361,0 | 355,1 |
| 200 | Throughput (task/s) | 0,610 | 0,555 | 0,563 |
| 200 | Utilisasi CPU (%) | 84,6 | 77,5 | 79,5 |
| 200 | Load imbalance | 0,06 | 0,13 | 0,13 |
| 200 | Energi (Wh) | 96,5 | 104,1 | 102,9 |
| 1.000 | Makespan (s) | 1.549,5 | 1.584,3 | 1.584,6 |
| 1.000 | Throughput (task/s) | 0,645 | 0,631 | 0,631 |
| 1.000 | Utilisasi CPU (%) | 96,5 | 94,5 | 94,5 |
| 1.000 | Load imbalance | 0,02 | 0,03 | 0,03 |
| 1.000 | Energi (Wh) | 466,2 | 474,4 | 474,6 |
| 5.000 | Makespan (s) | 7.257,0 | 7.251,7 | 7.250,5 |
| 5.000 | Throughput (task/s) | 0,689 | 0,689 | 0,690 |
| 5.000 | Utilisasi CPU (%) | 98,9 | 99,0 | 99,0 |
| 5.000 | Load imbalance | 0,00 | 0,00 | 0,00 |
| 5.000 | Energi (Wh) | 2.194,8 | 2.193,4 | 2.193,2 |
| 10.000 | Makespan (s) | 14.308,2 | 14.298,9 | 14.298,1 |
| 10.000 | Throughput (task/s) | 0,699 | 0,699 | 0,699 |
| 10.000 | Utilisasi CPU (%) | 99,4 | 99,4 | 99,4 |
| 10.000 | Load imbalance | 0,00 | 0,00 | 0,00 |
| 10.000 | Energi (Wh) | 4.331,2 | 4.328,9 | 4.328,7 |

### 6.4 Waktu tunggu terlama dan task lewat deadline (rata-rata 3 simulasi)

| n | Metrik | FCFS | SJF | HRRN |
|---|---|---|---|---|
| 60 | Waktu tunggu terlama (s) | 14,1 | 26,0 | 23,2 |
| 60 | Task lewat deadline | 0,0 | 0,0 | 0,0 |
| 200 | Waktu tunggu terlama (s) | 199,8 | 215,8 | 189,6 |
| 200 | Task lewat deadline | 88,0 | 3,0 | 2,0 |
| 1.000 | Waktu tunggu terlama (s) | 1.405,6 | 1.416,5 | 1.403,5 |
| 1.000 | Task lewat deadline | 885,3 | 821,3 | 883,0 |
| 5.000 | Waktu tunggu terlama (s) | 7.085,7 | 7.098,6 | 7.082,2 |
| 5.000 | Task lewat deadline | 4.890,3 | 4.763,0 | 4.913,3 |
| 10.000 | Waktu tunggu terlama (s) | 14.127,8 | 14.129,3 | 14.112,8 |
| 10.000 | Task lewat deadline | 9.889,0 | 9.720,0 | 9.907,7 |

### 6.5 Aturan pemilihan VM (HRRN, n = 200, rata-rata 5 seed)

Bagian ini berasal dari percobaan sebelum revisi (5 seed arrival) dan tidak diulang pada eksperimen revisi.

| Aturan VM | Makespan (s) | Energi (Wh) |
|---|---|---|
| `fast` | 354,4 | 102,7 |
| `green` | 347,6 | 101,1 |

---

## 7. Analisis

**1. Hasil pada beban ringan dan beban berat tidak sama.** Ini jawaban atas pertanyaan revisi. Pada n = 60 sistem hanya terpakai sekitar 50%, antrean jarang terbentuk, dan ketiga algoritma hampir tidak berbeda: selisih waktu tunggu sekitar 1 detik dan selisih response sekitar 1,5 detik dari sekitar 31 detik. Mulai n = 200 sistem padat (utilisasi 78-85%, lalu 95-99% pada n ≥ 1.000) dan perbedaan antar algoritma menjadi besar.

**2. Pada beban berat, HRRN dan SJF memangkas waktu tunggu dan response sekitar separuh dibanding FCFS, dan besarnya stabil.** Waktu tunggu rata-rata HRRN lebih rendah 51,5% (n = 200), 49,1% (n = 1.000), 48,1% (n = 5.000), dan 48,3% (n = 10.000). Response rata-rata turun 39,0% pada n = 200 dan 47-48% pada n ≥ 1.000. Jadi keuntungan relatif tidak membesar terus seiring beban, melainkan mendatar di sekitar 48%.

**3. HRRN dan SJF makin mirip saat beban makin berat.** SJF selalu sedikit lebih rendah pada waktu tunggu rata-rata, tetapi selisihnya mengecil: 6,7% pada n = 200, lalu 0,6%, 0,4%, dan 0,2% pada n = 1.000, 5.000, dan 10.000. Dugaan kami, karena semua task datang dalam 60 detik pertama sedangkan antrean baru habis setelah ribuan detik, waktu tunggu semua task menjadi hampir sama besar sehingga urutan RR pada HRRN hampir sama dengan urutan panjang task pada SJF. Dugaan ini tidak kami uji secara terpisah.

**4. Keunggulan HRRN pada task lewat deadline hanya muncul di beban menengah.** Pada n = 200, FCFS melewatkan deadline 44,0% task, sedangkan SJF 1,5% dan HRRN 1,0%. Pada n ≥ 1.000 sistem terlalu padat untuk rumus deadline yang kami pakai: 82-99% task lewat deadline di semua algoritma. Pada kondisi ini SJF yang paling sedikit (82,1% pada n = 1.000), sedangkan HRRN setara FCFS (88,3% vs 88,5%). Pada n = 60 tidak ada task yang lewat deadline.

**5. Waktu tunggu terlama: HRRN terendah pada beban menengah, lalu selisihnya hilang.** Pada n = 200, waktu tunggu terlama HRRN (189,6 s) sekitar 12% di bawah SJF (215,8 s) dan 5% di bawah FCFS (199,8 s), sejalan dengan tujuan HRRN mencegah starvation. Pada n ≥ 1.000 HRRN masih yang terendah, tetapi selisihnya di bawah 1% karena waktu tunggu terlama praktis ditentukan oleh panjang antrean total. Pada n = 60 urutannya berbeda: FCFS terendah (14,1 s), HRRN 23,2 s, SJF 26,0 s.

**6. Makespan dan energi hanya berbeda pada beban ringan sampai menengah.** FCFS memiliki makespan terendah pada n = 60 (155,9 s vs 162,9 s HRRN dan 177,9 s SJF), n = 200 (329,0 vs 355,1 dan 361,0), dan n = 1.000 (selisih sekitar 2%). Pada n = 5.000 dan 10.000 selisih makespan ketiga algoritma di bawah 0,1%, karena semua VM sibuk hampir sepanjang waktu (utilisasi 99%) sehingga urutan antrean tidak lagi mengubah total waktu kerja. Pada n = 60 selisihnya besar tetapi tidak stabil antar simulasi (makespan SJF 163,9 sampai 204,6 s), sehingga dari 3 simulasi kami tidak menyimpulkan pemenang makespan di beban ringan.

**7. Energi mengikuti makespan.** Draf desain memperkirakan makespan dan energi bisa bertentangan. Pada simulasi ini urutan energi selalu sama dengan urutan makespan di semua ukuran workload. Penyebabnya asumsi model energi: host menyala terus, dan daya idle (860 W untuk seluruh host) jauh lebih besar daripada daya tambahan saat task berjalan. Aturan VM `green` (bagian 6.5) memberi hasil sedikit lebih baik pada kedua metrik (selisih sekitar 2%), tetapi selisih sekecil ini belum cukup untuk menyatakan `green` lebih baik. Untuk memunculkan trade-off makespan vs energi, model perlu mampu mematikan host yang menganggur, dan hal itu belum dilakukan.

---

## 8. Batasan

1. **Dataset sintetis.** Workload dibangkitkan sendiri, belum memakai dataset cloud publik (misalnya GoCJ) seperti yang diusulkan draf.
2. **Variasi terbatas.** Panjang task selalu memakai seed yang sama (42); hanya arrival time yang divariasikan, dan hanya 3 simulasi per kombinasi. Selisih kecil (beberapa persen) tidak dapat dianggap signifikan secara statistik, dan tidak dilakukan uji statistik.
3. **Beban berat berarti antrean menumpuk.** Jendela kedatangan tetap 0-60 detik, sehingga pada n ≥ 1.000 hampir semua task sudah mengantre sejak awal dan kondisinya mendekati skenario "semua task datang bersamaan". Hasil beban berat tidak mewakili kondisi kedatangan terus-menerus dengan laju tinggi.
4. **Asumsi kelompok.** Kecepatan VM, komposisi host, rumus deadline, dan model energi adalah asumsi, bukan data nyata.
5. **Fitur draf yang belum ada.** Dependency antar task belum diimplementasikan, priority tidak dipakai, dan average execution time tidak dilaporkan terpisah.
6. **Distribusi VM antar datacenter timpang** (6 VM di DC 1, 14 VM di DC 2) karena perilaku broker bawaan.
7. **Mekanisme beberapa kendala teknis** (bagian 5.2, poin 1 dan 2) dibuktikan lewat percobaan perilaku, tetapi tidak diselidiki sampai ke kode internal CloudSim Plus.

---

## 9. Kesimpulan

1. Simulasi cloud heterogen (2 datacenter, 10 host, 20 VM) dan scheduler HRRN berhasil dibangun di CloudSim Plus, dan logika HRRN terverifikasi dengan perhitungan manual.
2. Hasil beban ringan dan beban berat **tidak sama**. Pada 60 task ketiga algoritma hampir tidak berbeda; pada 200 sampai 10.000 task, HRRN dan SJF menurunkan waktu tunggu rata-rata sekitar 48-52% dan response 39-48% dibanding FCFS.
3. HRRN dan SJF hampir sama pada metrik rata-rata, dan makin mirip saat beban makin berat (selisih waktu tunggu 6,7% pada 200 task, 0,2% pada 10.000 task).
4. Keunggulan khas HRRN (waktu tunggu terlama lebih rendah, task lewat deadline lebih sedikit) terlihat pada beban menengah (200 task). Pada beban sangat berat keunggulan itu hilang: waktu tunggu terlama ketiga algoritma berselisih di bawah 1% dan hampir semua task lewat deadline.
5. Makespan dan energi hanya berbeda antar algoritma pada beban ringan sampai menengah (FCFS terendah); pada 5.000 dan 10.000 task selisihnya di bawah 0,1%. Energi mengikuti makespan pada model energi yang dipakai.

**Saran pengembangan:** memakai dataset publik dengan variasi panjang task, menambah jumlah seed dan uji statistik, menambahkan dependency antar task, serta memperluas model energi dengan mematikan host menganggur agar trade-off makespan vs energi dapat diamati.

---

## Lampiran A. Cara menjalankan

### A.1 Prasyarat

- **JDK 21** dan **Maven** (dependensi `org.cloudsimplus:cloudsimplus:8.5.2` diunduh otomatis oleh Maven saat pertama kali dijalankan, jadi perlu koneksi internet).
- Cek instalasi dengan `java -version` dan `mvn -v`. Di macOS keduanya bisa dipasang dengan `brew install maven` (sudah membawa JDK); di Windows/Linux pasang JDK 21 dan Maven lalu pastikan keduanya ada di `PATH`.
- Semua perintah dijalankan dari folder utama proyek (folder yang berisi `pom.xml`).

### A.2 Eksperimen revisi (hasil bagian 6.2 sampai 6.4)

Satu perintah menjalankan 5 ukuran workload × 3 algoritma × 3 simulasi (45 simulasi, sekitar 1 menit) lewat `Tahap5.java`:

```bash
mvn -q compile exec:java -Dexec.mainClass=org.example.Tahap5 > hasil_revisi.txt
```

Hasilnya tersimpan di `hasil_revisi.txt`. Rata-rata per algoritma ada di bagian paling bawah file, atau bisa disaring dengan:

```bash
grep '^RATA' hasil_revisi.txt
```

- Baris `RUN` berisi hasil satu simulasi (seed arrival = nomor simulasi).
- Baris `RATA` berisi rata-rata semua simulasi untuk satu algoritma pada satu ukuran workload.
- Program berhenti dengan error jika ada simulasi yang cloudlet-nya tidak selesai semua.

Pilihan tambahan:

| Opsi | Bawaan | Keterangan |
|---|---|---|
| `-Dns=60,200,1000` | `60,200,1000,5000,10000` | Daftar ukuran workload (jumlah cloudlet) |
| `-Druns=5` | `3` | Jumlah simulasi per algoritma (memakai seed arrival 1 sampai `runs`) |
| `-DvmRule=green` | `fast` | Aturan pemilihan VM |

```bash
# contoh: hanya 60 dan 200 cloudlet, 5 simulasi per algoritma
mvn -q compile exec:java -Dexec.mainClass=org.example.Tahap5 -Dns=60,200 -Druns=5
```

### A.3 Satu simulasi saja

`Tahap4.java` menjalankan satu simulasi (dengan `Tahap1.java` sebagai pembuat host dan VM):

```bash
# satu kebijakan (fcfs | sjf | hrrn), jumlah task n, seed arrival
mvn -q compile exec:java -Dexec.mainClass=org.example.Tahap4 \
    -Dpolicy=hrrn -Dn=200 -Dseed=1 | grep HASIL

# aturan pemilihan VM (fast | green) dan baris energi
mvn -q compile exec:java -Dexec.mainClass=org.example.Tahap4 \
    -Dpolicy=hrrn -DvmRule=green -Dn=200 -Dseed=1 | grep EKSTRA
```

Format keluaran `HASIL`: jumlah selesai, makespan, response, waktu tunggu, throughput, utilisasi, load imbalance, dan jumlah task lewat deadline. Format keluaran `EKSTRA`: makespan, waktu tunggu terlama, dan energi (Wh).

### A.4 Uji logika HRRN (bagian 2.1)

```bash
mvn -q compile exec:java -Dexec.mainClass=org.example.HrrnUji
```

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
