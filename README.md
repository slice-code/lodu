# EduLocal AI 🎓💻🎨

EduLocal AI adalah aplikasi Android pembelajaran luring (offline) yang cerdas dan hemat daya. Aplikasi ini menjalankan Model Bahasa Besar (LLM), Generator Gambar (Stable Diffusion), Pengenal Gambar (Vision Chat), dan sistem basis data dokumen (RAG) secara 100% lokal, privat, dan bebas token di perangkat mobile Anda.

---

## 🌟 Fitur Utama

### 1. 💬 Obrolan Offline & Asisten AI Spesialis
* **Karakter Mentor AI**: Diskusi interaktif dengan mentor spesifik seperti *General Tutor AI* (sains & matematika), *Software Developer* (koding & kotlin), *Graphic Designer* (kritik & inspirasi tata letak), atau *Copywriter Expert* (penyusunan esai/artikel).
* **Streaming Respon Real-Time**: Respon di-stream kata demi kata secara instan menggunakan akselerasi perangkat keras lokal Anda.
* **Riwayat Obrolan Terpadu**: Panel laci navigasi modern (Navigation Drawer) untuk membuat sesi belajar baru, menghapus sesi lama, mengubah nama topik belajar, serta menyimpan seluruh pesan secara aman di Room Database lokal.
* **Format Markdown Premium**: Mendukung rendering teks berformat tebal (`**bold**`), daftar poin (`- list`), kode pemrograman inline (`` `code` ``), serta blok kode pemrograman multiline (` ```code``` `) dengan wadah editor bertema gelap yang interaktif dan dapat digeser secara horizontal.

### 2. 🎨 Studio Sketsa & Diagram (Stable Diffusion Offline)
* **Kompilasi MNN Vulkan & QNN NPU**: Pembuatan gambar/sketsa diagram edukasi menggunakan akselerasi GPU Vulkan atau Snapdragon NPU.
* **Setelan Kreatif Tingkat Lanjut**: Pengaturan CFG Scale, jumlah langkah kompilasi (steps), rasio gambar (aspect ratio), model LoRA, seed kustom, dan prompt negatif.
* **Auto-Resizing Prompt**: Kotak input prompt positif & negatif yang otomatis membesar secara vertikal agar deskripsi visual yang panjang tetap terlihat jelas.
* **Galeri Sketsa**: Menyimpan hasil lukisan, memasukkannya langsung ke obrolan sebagai lampiran gambar, atau mengekspornya ke penyimpanan galeri ponsel.

### 3. 📚 RAG (Retrieval-Augmented Generation) & Vision Chat
* **Konteks Dokumen Lokal**: Unggah dokumen pelajaran format teks (`.txt`), konversikan menjadi basis data vektor embedding menggunakan ONNX Runtime lokal secara offline, lalu tanyakan informasi dari dokumen tersebut pada AI.
* **Analisis Visual (Multimodal)**: Unggah gambar materi, diagram, atau objek fisik melalui kamera/galeri untuk dibahas secara offline oleh AI Vision.

### 4. ⚙️ Manajemen Driver & Deteksi Kompatibilitas Perangkat
* **Akselerasi Driver Hardware**: Memilih kompiler backend langsung dari antarmuka Settings (pilihan CPU, GPU Vulkan, Qualcomm QNN NPU).
* **Deteksi Spesifikasi Perangkat Otomatis**: Membaca kapasitas RAM ponsel dan jenis chipset secara real-time. Menampilkan status badge kompatibilitas dinamis (misal: badge rekomendasi model untuk RAM 8GB atau peringatan lag untuk model berat).
* **Proteksi Download Aman**: Menonaktifkan tombol unduh secara dinamis untuk jenis model yang tidak kompatibel dengan arsitektur chipset ponsel Anda (seperti model SDXL khusus Snapdragon di ponsel non-Qualcomm).

### 5. 📥 Layanan Unduhan Latar Belakang & WakeLock
* **Foreground Service**: Pengunduhan file model berjalan di latar belakang menggunakan Notifikasi Sistem (Foreground Notification) yang menampilkan progress bar real-time.
* **Stabilitas WakeLock**: Menggunakan `PARTIAL_WAKE_LOCK` agar CPU ponsel tetap aktif menyelesaikan unduhan model berukuran besar sekalipun layar ponsel padam/mati.

---

## 🗂️ Format Model yang Didukung

Untuk memuat model ke dalam aplikasi (baik melalui download bawaan maupun impor eksternal), pastikan model Anda menggunakan format file berikut:

| Kategori AI | Format File | Deskripsi & Engine Runtime |
| :--- | :---: | :--- |
| **LLM Chat & Koding** | `.task` | Google LiteRT (TensorFlow Lite) / MediaPipe LLM Inference format. |
| **Stable Diffusion** | `.bundle` | Paket folder/direktori kompilasi bobot MNN (Mobile Neural Network) atau Qualcomm QNN. |
| **Vision (Multimodal)** | `.task` | MediaPipe Vision Task format. |
| **RAG Embeddings** | `.onnx` | Model embedding ONNX Mobile Runtime (misal: *bge-small-en*). |

---

## 📥 Cara Impor Model Kustom Eksternal (HuggingFace)

Jika Anda mengunduh model secara mandiri (misalnya model berformat `.task` atau `.bundle` dari HuggingFace), Anda dapat memuatnya ke dalam aplikasi dengan cara:
1. Masuk ke tab **Pengaturan** (ikon roda gigi di kanan bawah).
2. Klik tombol tambah (**`+`**) di pojok kanan atas TopAppBar.
3. Klik tombol **Pilih File Model** untuk membuka pemilih berkas sistem Android, lalu cari model kustom Anda di memori HP.
4. Masukkan nama tampilan model kustom Anda.
5. Tentukan kategori model (LLM Chat, SD Image, Vision, atau RAG Vector).
6. Tekan tombol **Impor** untuk menyalin model ke dalam ruang penyimpanan internal EduLocal AI secara otomatis.

> [!NOTE]  
> Jika Anda mengunduh model mentah dengan format aslinya seperti PyTorch (`.safetensors` atau `.bin`) atau `.gguf`, Anda harus mengonversinya terlebih dahulu ke format `.task` menggunakan pustaka Python **MediaPipe Model Converter** resmi sebelum dapat dimuat oleh aplikasi.

---

## 🛠️ Panduan Menjalankan & Membangun Project

### Prasyarat
* [Android Studio (Koala atau versi terbaru)](https://developer.android.com/studio)
* Android SDK 34 / JDK 17
* Ponsel Android Fisik dengan RAM minimal 8GB (direkomendasikan untuk uji coba performa luring)

### Langkah Pemasangan
1. **Clone** atau buka direktori project ini di Android Studio.
2. Tunggu sinkronisasi **Gradle** hingga selesai.
3. Buat file `.env` di direktori utama project (sejajar dengan `build.gradle.kts` utama), lalu tambahkan key API jika diperlukan (bisa disalin dari `.env.example`).
4. Sambungkan ponsel Android fisik Anda, aktifkan *USB Debugging*.
5. Jalankan aplikasi dengan menekan tombol **Run** di toolbar Android Studio.

### Perintah Gradle Berguna
* **Kompilasi Kotlin Debug**:
  ```bash
  ./gradlew compileDebugKotlin
  ```
* **Membangun APK Release**:
  ```bash
  ./gradlew assembleRelease --no-daemon
  ```
  *Output APK hasil rilis dapat ditemukan di:* `app/build/outputs/apk/release/app-release-unsigned.apk`
