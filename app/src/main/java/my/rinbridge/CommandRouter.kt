package my.rinbridge

import java.util.Locale

enum class RinCommand {
    PLAY,
    PAUSE,
    STOP,
    NEXT,
    PREVIOUS,
    OPEN_MUSICOLET,
    OPEN_CAMERA,
    OPEN_MAPS,
    OPEN_CALENDAR,
    OPEN_CLOCK,
    OPEN_SETTINGS,
    OPEN_DIALER,
    OPEN_MESSAGES,
    OPEN_WHATSAPP,
    UNKNOWN
}

object CommandRouter {
    fun parse(input: String): RinCommand {
        val text = input.lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")

        return when {
            text.contains("buka kamera") || text.contains("open camera") ||
            text.contains("ambil gambar") || text.contains("take a photo") ->
                RinCommand.OPEN_CAMERA

            text.contains("buka peta") || text.contains("buka maps") ||
            text.contains("google maps") || text.contains("open maps") ||
            text.contains("navigasi") ->
                RinCommand.OPEN_MAPS

            text.contains("buka kalendar") || text.contains("buka calendar") ||
            text.contains("open calendar") || text.contains("tengok kalendar") ->
                RinCommand.OPEN_CALENDAR

            text.contains("buka jam") || text.contains("buka alarm") ||
            text.contains("buka penggera") || text.contains("open clock") ||
            text.contains("show alarms") ->
                RinCommand.OPEN_CLOCK

            text.contains("buka tetapan") || text.contains("buka settings") ||
            text.contains("open settings") || text.contains("tetapan telefon") ->
                RinCommand.OPEN_SETTINGS

            text.contains("buka telefon") || text.contains("buka dialer") ||
            text.contains("open phone") || text.contains("open dialer") ||
            text.contains("buat panggilan") ->
                RinCommand.OPEN_DIALER

            text.contains("buka mesej") || text.contains("buka messages") ||
            text.contains("open messages") || text.contains("hantar mesej") ->
                RinCommand.OPEN_MESSAGES

            text.contains("buka whatsapp") || text.contains("open whatsapp") ->
                RinCommand.OPEN_WHATSAPP

            text.contains("buka musicolet") || text.contains("open musicolet") ->
                RinCommand.OPEN_MUSICOLET

            text.contains("lagu seterusnya") || text.contains("lagu selepas ini") ||
            text.contains("skip lagu") || text.contains("next song") || text == "next" ->
                RinCommand.NEXT

            text.contains("lagu sebelumnya") || text.contains("lagu sebelum") ||
            text.contains("lagu lepas") || text.contains("previous song") || text == "previous" ->
                RinCommand.PREVIOUS

            text.contains("hentikan muzik") || text.contains("henti muzik") ||
            text.contains("berhenti muzik") || text.contains("stop music") ||
            text == "stop" || text == "henti" ->
                RinCommand.STOP

            text.contains("berhenti sekejap") || text.contains("jeda muzik") ||
            text.contains("pause music") || text == "pause" || text == "pause muzik" ->
                RinCommand.PAUSE

            text.contains("mainkan muzik") || text.contains("main muzik") ||
            text.contains("main lagu") || text.contains("mula muzik") ||
            text.contains("sambung muzik") || text.contains("teruskan muzik") ||
            text.contains("play music") || text == "play" ->
                RinCommand.PLAY

            else -> RinCommand.UNKNOWN
        }
    }
}
