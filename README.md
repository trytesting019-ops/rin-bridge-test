# Rin Bridge Test 0.1

Prototaip sumber Android untuk ujian awal pada Doogee S200 Plus.

## Fungsi yang ada
- Speech-to-Text melalui Android Recognizer Intent (permintaan bahasa ms-MY).
- Text-to-Speech melalui enjin TTS Android (cuba ms-MY, fallback en-US).
- Butang ujian untuk membuka WhatsApp.
- Accessibility Service pilihan yang memeriksa teks peristiwa daripada aplikasi ChatGPT untuk penanda tepat `RIN_BRIDGE:OPEN_WHATSAPP`.

## Batasan penting
- Ini **projek sumber**, bukan APK siap atau telah dipasang.
- Accessibility Service perlu diaktifkan sendiri melalui tetapan Android. Ia mempunyai akses sensitif kepada kandungan skrin; aktifkan hanya jika memahami risikonya.
- Android/versi ChatGPT mungkin tidak mendedahkan teks mesej melalui accessibility event. Jika penanda tidak dapat dikesan, laluan itu tidak akan berfungsi dan perlu diuji pada telefon.
- Android boleh menyekat pelancaran aktiviti dari latar belakang. Butang ujian dalam aplikasi ialah laluan manual yang lebih boleh diharap.
- STT mungkin memerlukan enjin pengecaman suara yang dipasang dan rangkaian; projek ini tidak menjanjikan operasi luar talian.
- Prototaip ini tidak menghantar data ke pelayan Rin atau mana-mana pelayan tersuai. Enjin pengecaman/TTS yang dipilih Android mungkin mempunyai dasar rangkaian dan privasi tersendiri.
- Belum ada integrasi automatik dua hala dengan ChatGPT. Penanda arahan perlu benar-benar muncul dalam teks yang boleh dibaca oleh Accessibility Service.

## Cara bina
1. Ekstrak ZIP.
2. Buka folder `RinBridge_Test` dalam Android Studio.
3. Benarkan Gradle sync dan pasang Android SDK Platform 35 jika diminta.
4. Bina `app` > `assembleDebug`.
5. Pasang APK debug pada telefon dan buka `Rin Bridge Test`.

## Ujian pertama
1. Tekan `Uji suara Rin (TTS)`.
2. Tekan `Cakap → tulisan (STT)` dan sebut "buka WhatsApp".
3. Tekan `Uji buka WhatsApp`.
4. Hanya selepas ujian asas berjaya, pertimbangkan mengaktifkan Accessibility Service melalui butang tetapan. Uji penanda `RIN_BRIDGE:OPEN_WHATSAPP` pada skrin ChatGPT.

## Konfigurasi
- Package aplikasi: `my.rinbridge.test`
- Minimum Android: API 26
- Sasaran Android: API 32 (untuk prototaip awal)
- Penanda arahan: `RIN_BRIDGE:OPEN_WHATSAPP`
