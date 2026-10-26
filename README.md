# Chat iOS 27 (Android) — Feng iOS on Droid

Pesan bergaya iMessage lewat data internet (bukan SMS). Kedua pihak harus memakai aplikasi ini.

- Akun: username + password (Firebase Authentication).
- Pesan: Firebase Realtime Database milikmu sendiri (REST + streaming).
- Profil dengan foto dan bio, bisa dilihat teman. Kode QR untuk menambah teman (ios27chat://u/USERNAME).
- Tekan lama gelembung: reaksi, salin, hapus.
- Panggilan suara (WebRTC): hanya berdering saat aplikasi terbuka. Di sebagian jaringan seluler perlu server relay (TURN) di Settings.
- Kirim foto (dikompres), stiker (perpustakaan sendiri), dan video pendek (maks. 3 MB) lewat Realtime Database.
- Gaya obrolan iOS 18 atau iOS 26 (Settings > Messages > Chat style): bilah atas/bawah dan popup gelembung terangkat (iOS 26).
- Profil gaya kontak iOS dengan Block Contact (hanya di HP ini).
- Emoji: pilih berkas font emoji (.ttf) sendiri di Settings.

## Firebase
1. Authentication > Email/Password > Enable.
2. Realtime Database > Rules: tempel isi firebase-rules.json (juga lewat tombol "Copy security rules" di aplikasi) > Publish.
   Setelah memperbarui aplikasi, tempel dan Publish ulang aturan ini (ada tambahan untuk panggilan).
3. Aplikasi > Server setup > isi Web API Key dan alamat database.

Foto, stiker, dan video disimpan di database (bukan Firebase Storage), jadi memakai kuota 1 GB / 10 GB paket gratis.

Pesan tidak dienkripsi ujung ke ujung dan tersimpan di proyek Firebase milikmu.
