# Reguli ProGuard pentru Room și Vosk offline
-keep class ro.stupina.offline.data.db.** { *; }
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**
