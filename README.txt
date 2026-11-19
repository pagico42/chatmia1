CHAT LOCALE ANDROID - progetto sorgente
=======================================

Compatibilità prevista: Android 4.0.4 (API 15) e successivi.
Protocollo: ChatReteMultiV8 PowerShell, porta predefinita TCP 5050.

CONTENUTO
- app/src/main/java/.../MainActivity.java: interfaccia e motore TCP.
- AndroidManifest.xml: permessi minimi.
- Gradle: Android Gradle Plugin 3.2.1, compileSdk 28, minSdk 15.

COMPILAZIONE
Questo è il progetto sorgente, non un APK già compilato.
Per generare l'APK serve Android SDK / Build Tools 28.0.3 e una versione Java/Gradle
compatibile con Android Gradle Plugin 3.2.1. Aprire la cartella in Android Studio
compatibile oppure compilare con Gradle:
    gradle assembleDebug
L'APK risultante sarà in app/build/outputs/apk/debug/app-debug.apk

TEST DI COMPATIBILITA' DA ESEGUIRE
1. Telefono e PC sulla stessa rete Wi-Fi/LAN.
2. Porta e codice uguali su entrambi (5050 e 1234 per default).
3. Su Windows consentire TCP 5050 nel firewall.
4. Provare prima PC -> Android e poi Android -> PC.
5. Provare testo, privato, invio file e ricezione file.

NOTE IMPORTANTI / LIMITI DELLA PRIMA BOZZA
- Android 4.0.4 è molto datato: alcuni selettori file e dispositivi possono avere
  comportamenti diversi.
- La ricezione/sending in questa prima versione avviene mentre l'Activity è aperta;
  non è ancora un servizio persistente in background.
- Per semplicità l'invio file legge il file in memoria: per file molto grandi
  occorre sostituirlo con streaming a blocchi.
- La riconnessione usa gli IP salvati, ma il salvataggio dello storico viene
  completato quando si salva la configurazione/si avvia la chat.
- Il comportamento deve essere verificato su dispositivo reale prima di dichiarare
  piena compatibilità. Non è stato possibile compilare/eseguire qui l'APK.
