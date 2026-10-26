# PDF Studio — Audit dan Changelog

Tanggal audit: 26 Oktober 2026  
Basis: `pdf-studio-fixed.zip`  
Jenis pemeriksaan: inspeksi source statis, validasi JSON/XML, dan pemeriksaan artefak build.

## Ringkasan

Source ini dipilih sebagai basis karena strategi kompresinya mengompresi gambar yang tertanam di PDF, bukan merasterisasi seluruh halaman. Perubahan di bawah adalah perbaikan defensif yang dapat diturunkan langsung dari kode. Ini bukan klaim bahwa aplikasi sudah lolos build atau pengujian perangkat.

## Perubahan yang dibuat

1. **Merge PDF lebih aman**
   - Menyaring berkas sumber yang tidak ada atau kosong.
   - Menghapus output lama/parsial sebelum merge agar berkas lama tidak keliru dianggap hasil baru.
   - Memvalidasi output dengan membuka ulang PDF dan memastikan ada halaman.
   - Membersihkan output parsial ketika merge gagal; fallback hanya melaporkan sukses jika hasilnya tidak kosong.

2. **Ekspor halaman gambar lebih tahan terhadap dokumen besar**
   - Menutup `PdfRenderer.Page` dan mendaur ulang bitmap melalui `finally`, termasuk saat rendering atau penulisan file gagal.
   - Membatasi hasil render sampai sekitar 20 juta piksel per halaman untuk mengurangi risiko kehabisan memori pada halaman dengan dimensi ekstrem.
   - Memeriksa nilai balik `Bitmap.compress()` dan menghapus file ekspor yang gagal ditulis.

3. **Kompresi PDF mengurangi tekanan memori**
   - Mengganti perbandingan stream menggunakan `readBytes()` dengan pembacaan buffer bertahap.
   - Melepas bitmap hasil decode dan bitmap hasil scaling setelah proses encoding JPEG.
   - Menghapus output parsial jika kompresi gagal.

4. **Impor berkas SAF lebih defensif**
   - `copyUriToTempFile()` kini melempar error jika stream tidak dapat dibuka atau hasil salinan kosong, dan menghapus file parsial.
   - Alur tambah PDF ke daftar merge melewati file yang tidak bisa dibaca sebagai PDF dan memberi status error yang menjelaskan jumlah file yang dilewati.

5. **Metadata kemampuan diperbarui**
   - `metadata.json` kini berisi daftar kapabilitas utama yang sesuai dengan fungsi aplikasi.

## Pemeriksaan yang benar-benar dilakukan

- `metadata.json` berhasil diparse sebagai JSON.
- File XML resource berhasil diparse.
- 18 file Kotlin diperiksa untuk conflict marker (`<<<<<<<` / `>>>>>>>`); tidak ditemukan.
- **Build Android belum dijalankan.** Arsip tidak menyertakan `gradle-wrapper.jar`, dan lingkungan audit tidak memiliki Android SDK/`ANDROID_HOME` maupun instalasi `gradle` yang dapat dipakai. Karena itu tidak ada klaim `assembleDebug`, unit test, atau uji perangkat yang lulus.

## Risiko yang masih perlu diuji

- Gradle Sync dan `:app:assembleDebug` di Android Studio dengan JDK/Android SDK yang sesuai.
- Merge PDF terenkripsi, rusak, tanpa halaman, atau berisi resource/anotasi kompleks.
- Pengelolaan halaman yang memiliki anotasi, form, tautan, dan resource bersama.
- Kompresi PDF dengan soft mask, transparency, image mask, gambar scan, dan gambar JPEG berukuran besar.
- DOCX dengan tabel, gambar, daftar, header/footer, font khusus, dan pemformatan kompleks. Parser native bukan mesin render Word penuh.
- Berbagi satu file dan banyak file melalui FileProvider di Android versi target.
- Pengujian memori pada PDF besar di perangkat fisik/emulator.

## Checklist uji manual yang disarankan

1. Impor PDF valid, PDF kosong, file non-PDF yang diganti ekstensi, dan URI yang tidak lagi dapat dibaca.
2. Urutkan 3+ halaman, hapus beberapa halaman, sisipkan halaman kosong, simpan, lalu buka ulang PDF hasil.
3. Merge dua PDF dan tiga PDF dengan urutan berbeda; verifikasi jumlah dan urutan halaman hasil.
4. Konversi 3 gambar JPG/PNG ke PDF dan DOCX sampel ke PDF; buka hasilnya kembali.
5. Baca PDF panjang, lakukan pinch zoom/pan, ubah mode baca, dan navigasi melalui slider/thumbnail.
6. Kompres PDF berbasis scan dan PDF berbasis teks; bandingkan ukuran, keterbacaan, dan kemampuan memilih teks.
7. Ekspor beberapa halaman ke PNG/JPG dan ekstrak teks; coba bagikan hasil ke aplikasi lain.

## Catatan perilaku kompresi

Kompresi gambar tertanam tidak menjamin penghematan persentase tertentu. PDF berbasis teks/vektor dapat hampir tidak mengecil. Hasil akhir harus dinilai dari ukuran file dan kualitas visual aktual.
