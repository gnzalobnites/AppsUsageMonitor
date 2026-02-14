package com.gnzalobnites.appsusagemonitor.banner

/**
 * Estados posibles del banner
 */
enum class BannerState {
    HIDDEN,           // Oculto, esperando próximo intervalo
    VISIBLE_WAITING,  // Visible, esperando que usuario interactúe
    VISIBLE_EXPANDED  // Expandido por el usuario
}

/**
 * Configuración visual del banner
 */
data class BannerVisualConfig(
    val accentColor: Int,
    val backgroundColor: Int,
    val textColorPrimary: Int,
    val textColorSecondary: Int
)

/**
 * Mensajes motivacionales predefinidos
 */
object MotivationalMessages {
    val messages = listOf(
        "⏳ El tiempo es tu recurso más valioso",
        "👀 Sé consciente de dónde inviertes tu tiempo",
        "💡 ¿Estás usando este tiempo como realmente quieres?",
        "🎯 Cada minuto cuenta hacia tus objetivos",
        "🔄 Considera si necesitas un cambio de actividad",
        "📱 ¿Esta app te acerca a tus metas?",
        "🌟 Tu atención vale oro - ¿Dónde la pones?",
        "⚡ Este momento es una elección - ¿La estás haciendo consciente?",
        "🔔 Recordatorio: tú controlas tu tiempo",
        "🌱 Pequeños cambios en el uso diario crean grandes resultados"
    )
}