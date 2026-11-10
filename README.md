# RinBridge

Prototaip Android untuk Doogee S200 Plus dan Android head unit. Projek ini menyediakan arahan suara tempatan serta laluan arahan bertanda daripada teks ChatGPT yang kelihatan.

## Fungsi semasa
- Perkhidmatan suara latar depan menggunakan Android SpeechRecognizer.
- Kata panggilan: "Hey Rin", "Hei Rin" atau "Hai Rin".
- Kawalan muzik asas: main, jeda, henti, seterusnya dan sebelumnya.
- Buka Musicolet atau WhatsApp melalui arahan bertanda.
- Accessibility Service pilihan yang hanya memeriksa peristiwa daripada aplikasi ChatGPT (com.openai.chatgpt) dan mencari penanda arahan yang dibenarkan.

## Penanda arahan yang disokong
Letakkan satu penanda tepat pada teks yang kelihatan dalam ChatGPT:

- RIN_BRIDGE:OPEN_WHATSAPP
- RIN_BRIDGE:OPEN_MUSICOLET
- RIN_BRIDGE:PLAY
- RIN_BRIDGE:PAUSE
- RIN_BRIDGE:STOP
- RIN_BRIDGE:NEXT
- RIN_BRIDGE:PREVIOUS

Servis mengabaikan arahan yang tidak berada dalam senarai ini. Penanda yang sama tidak akan dijalankan berulang kali selagi ia kekal kelihatan; ia boleh dicetuskan semula selepas penanda hilang daripada paparan.

## Pasang dan aktifkan
1. Pasang APK debug yang dibina oleh GitHub Actions.
2. Buka RinBridge dan benarkan akses mikrofon jika mahu menggunakan "Hey Rin".
3. Untuk kawalan daripada ChatGPT, buka butang tetapan Accessibility dalam aplikasi.
4. Aktifkan RinBridge untuk ChatGPT secara manual pada tetapan Android.
5. Buka ChatGPT dan uji dengan satu penanda yang disenaraikan di atas.

## Privasi dan had penting
- Accessibility Service ialah kebenaran sensitif: apabila diaktifkan, Android membenarkannya membaca teks tetingkap ChatGPT yang dipaparkan. Ia ditapis kepada pakej ChatGPT dan tidak menghantar teks skrin ke pelayan tersuai.
- Pengaktifan Accessibility mesti dibuat sendiri oleh pemilik telefon.
- Android/versi ChatGPT mungkin tidak mendedahkan teks jawapan melalui accessibility tree. Jika begitu, penanda tidak akan dikesan; ini mesti disahkan pada peranti sebenar.
- Android boleh menyekat pelancaran aplikasi dari latar belakang bergantung pada versi dan keadaan peranti.
- Pengecaman suara Android mungkin memerlukan enjin bahasa dan rangkaian. Operasi luar talian tidak dijamin.
- Ini bukan sambungan API rasmi ChatGPT dan bukan kawalan jauh penuh. Ia hanya bertindak balas kepada penanda yang kelihatan dan arahan yang disenaraikan.

## Bina APK
GitHub Actions menjalankan workflow .github/workflows/build-apk.yml pada setiap push ke main. Selepas workflow berjaya, muat turun artifak RinBridge-debug-APK daripada tab Actions.

## Butiran projek
- Application ID: my.rinbridge
- Namespace: my.rinbridge
- Minimum Android: API 23
- Compile SDK: 35
- Target SDK: 35
- Java/Kotlin target: 17


## RinBridge Internet Relay (prototype)

The `cloudflare/` folder contains a small HTTPS command relay and an OpenAPI schema for a ChatGPT custom GPT Action.

### Security model
- The relay accepts only the fixed allow-list of commands in `worker.js`.
- ChatGPT Action authenticates with the server-side `ADMIN_TOKEN`.
- The Android device authenticates with a separate `DEVICE_TOKEN`.
- Tokens must be created as Cloudflare Worker secrets. Never commit tokens to this repository or put the admin token in the APK.
- The relay stores command names, device IDs, command IDs and timestamps only. Commands expire after 10 minutes.
- The Android app still needs a polling client and a pairing/settings screen before it can receive commands. This repository change does not yet complete that Android client.

### Cloudflare setup (server side)
1. Create a Cloudflare Worker named `rinbridge-relay`.
2. Create a Workers KV namespace and bind it as `COMMANDS`.
3. Replace the placeholder KV namespace ID in `cloudflare/wrangler.toml`.
4. Add Worker secrets `ADMIN_TOKEN` and `DEVICE_TOKEN` using long, random values. Keep them separate.
5. Deploy `cloudflare/worker.js` and test `GET /health`.
6. In a ChatGPT custom GPT, add an Action using `cloudflare/chatgpt-openapi.yaml`, replace `YOUR-WORKER` with the deployed Worker hostname, and configure Bearer authentication with the `ADMIN_TOKEN`.
7. Do not enable device commands until the Android polling client is installed and configured with the device token.

### Current status
This is the relay server and ChatGPT Action contract only. It is not deployed, no live endpoint exists yet, and the current Android APK does not poll this relay. End-to-end control is therefore not available yet.
