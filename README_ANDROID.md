# Stupina Mea — Proiect Android Studio (Kotlin + Jetpack Compose + Room + Offline Voice)

## 1. Structura proiectului Android
- `app/src/main/java/ro/stupina/offline/data/db/` — Baza de date locală Room (SQLite) cu cele 7 entități: `ApiaryEntity`, `HiveEntity`, `ObservationEntity`, `QueenEntity`, `ActivityEntity`, `ReminderEntity`, `EventEntity`.
- `app/src/main/java/ro/stupina/offline/data/repository/StupinaRepository.kt` — Operații atomice (divizare, unire, mutare, schimbare matcă, arhivare fără ștergerea istoricului).
- `app/src/main/java/ro/stupina/offline/voice/RomanianVoiceParser.kt` — Parser 100% offline pentru dictare și comenzi vocale în limba română.
- `app/src/main/java/ro/stupina/offline/voice/OfflineSpeechManager.kt` — Verifică mai întâi modelul local Vosk (`filesDir/vosk-model-ro`), apoi `SpeechRecognizer` cu `EXTRA_PREFER_OFFLINE=true`, și comută automat pe alternativa locală dacă lipsesc.
- `app/src/main/java/ro/stupina/offline/voice/TtsConfirmManager.kt` — Confirmări vocale offline `TextToSpeech` în `ro-RO`.
- `app/src/main/java/ro/stupina/offline/export/BackupAndReportManager.kt` — Export CSV UTF-8, Excel `.xls` (SpreadsheetML) și Backup/Restaurare completă JSON.
- `app/src/main/java/ro/stupina/offline/ui/` — Arhitectură MVVM (`StupinaViewModel`) + interfață Jetpack Compose cu butoane mari pentru utilizare cu mănuși și contrast ridicat la soare.

## 2. Cum generezi fișierul APK instalabil
1. Deschide folderul `android-project/` în **Android Studio** (Ladybug / Koala sau mai nou, cu JDK 17).
2. Așteaptă sincronizarea Gradle (`Sync Project with Gradle Files`).
3. Pentru APK de test instalabil direct pe telefon:
   - Din meniu: **Build > Build Bundle(s) / APK(s) > Build APK(s)**
   - Sau din terminal: `./gradlew assembleDebug` (Linux/Mac) / `gradlew.bat assembleDebug` (Windows)
   - Fișierul rezultat este în: `app/build/outputs/apk/debug/app-debug.apk`.
4. Copiază `app-debug.apk` pe telefonul Android și permite instalarea din surse necunoscute, sau instalează prin USB: `adb install app/build/outputs/apk/debug/app-debug.apk`.

## 3. Configurare recunoaștere vocală 100% offline în limba română
- **Opțiunea A (Google Offline Speech Pack)**: Pe telefon, deschide *Setări -> Sistem -> Limbi și introducere -> Recunoaștere vocală -> Google -> Recunoaștere vocală offline* și descarcă pachetul **Română (ro-RO)**.
- **Opțiunea B (Vosk 100% independent, fără servicii Google)**: Descarcă `vosk-model-small-ro` și copiază-l în directorul intern al aplicației `/data/data/ro.stupina.offline/files/vosk-model-ro` (sau în `app/src/main/assets/vosk-model-ro` la compilare).
- **Opțiunea C (Alternativă locală integrată)**: Dacă dispozitivul nu are niciun motor vocal offline instalat, aplicația detectează automat lipsa motorului și activează modul de dictare asistată / șabloane vocale rapide complet offline.
