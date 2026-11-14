package br.painel.g9

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.util.Locale

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

val DIM: Int = Color.parseColor("#9AA4B2")
val HEAD: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
val BODY: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dp(v: Int): Int = dp(v.toFloat())

fun cardBg(ctx: Context, fill: Int, stroke: Int, radiusDp: Float = 14f): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(fill)
    g.cornerRadius = ctx.dp(radiusDp).toFloat()
    g.setStroke(ctx.dp(1), stroke)
    return g
}

fun label(ctx: Context, text: String, sp: Float, color: Int, head: Boolean = false): TextView {
    val t = TextView(ctx)
    t.text = text
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    t.setTextColor(color)
    t.typeface = if (head) HEAD else BODY
    return t
}

fun fmt1(x: Float): String = String.format(Locale("pt", "BR"), "%.1f", x)
fun fmt2(x: Float): String = String.format(Locale("pt", "BR"), "%.2f", x)

fun loadBitmap(f: File, maxDim: Int): Bitmap? {
    if (!f.exists()) return null
    return try {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / s > maxDim || o.outHeight / s > maxDim) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        BitmapFactory.decodeFile(f.path, o2)
    } catch (e: Throwable) {
        null
    }
}

// ---------------------------------------------------------------- temas

class PTheme(
    val id: String,
    val nome: String,
    val bgTop: Int,
    val bgBottom: Int,
    val accent: Int,
    val card: Int
)

object Themes {
    private fun c(s: String) = Color.parseColor(s)

    val all = listOf(
        PTheme("asfalto", "Asfalto Vermelho", c("#2A0710"), c("#05070B"), c("#E5172F"), c("#CC111822")),
        PTheme("costa", "Costa Neon", c("#2B0A3D"), c("#04121F"), c("#FF3FA4"), c("#CC130B26")),
        PTheme("ambar", "Garagem Âmbar", c("#2A1608"), c("#0A0604"), c("#FF8A1F"), c("#CC1A110A")),
        PTheme("gelo", "Gelo", c("#0B2233"), c("#040A10"), c("#27C7FF"), c("#CC0E1B26"))
    )

    fun byId(id: String): PTheme = all.firstOrNull { it.id == id } ?: all[0]
}

// ---------------------------------------------------------------- preferências

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("painel", Context.MODE_PRIVATE)

    var tema: String
        get() = sp.getString("tema", "asfalto") ?: "asfalto"
        set(v) { sp.edit().putString("tema", v).apply() }

    var nome: String
        get() = sp.getString("nome", "Civic") ?: "Civic"
        set(v) { sp.edit().putString("nome", v).apply() }

    var boasVindas: Boolean
        get() = sp.getBoolean("boas", true)
        set(v) { sp.edit().putBoolean("boas", v).apply() }

    var limite: Int
        get() = sp.getInt("limite", 0)
        set(v) { sp.edit().putInt("limite", v).apply() }

    var obdTipo: String
        get() = sp.getString("obdTipo", "bt") ?: "bt"
        set(v) { sp.edit().putString("obdTipo", v).apply() }

    var obdMac: String
        get() = sp.getString("obdMac", "") ?: ""
        set(v) { sp.edit().putString("obdMac", v).apply() }

    var obdWifi: String
        get() = sp.getString("obdWifi", "192.168.0.10:35000") ?: "192.168.0.10:35000"
        set(v) { sp.edit().putString("obdWifi", v).apply() }

    var autoObd: Boolean
        get() = sp.getBoolean("autoObd", true)
        set(v) { sp.edit().putBoolean("autoObd", v).apply() }

    var preco: Float
        get() = sp.getFloat("preco", 6.0f)
        set(v) { sp.edit().putFloat("preco", v).apply() }

    var calib: Float
        get() = sp.getFloat("calib", 1.0f)
        set(v) { sp.edit().putFloat("calib", v).apply() }

    fun slot(i: Int): String = sp.getString("slot$i", DEFAULT_SLOTS[i]) ?: ""
    fun setSlot(i: Int, pkg: String) { sp.edit().putString("slot$i", pkg).apply() }

    companion object {
        val DEFAULT_SLOTS = listOf(
            "com.waze",
            "com.google.android.apps.maps",
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.projection.gearhead"
        )
    }
}

// ---------------------------------------------------------------- dados ao vivo

object Live {
    var gpsSpeed = 0f
    var gpsFix = false
    var lat = 0.0
    var lon = 0.0

    var obd: ObdData? = null
    var obdAt = 0L
    var obdState = "Desconectado"

    var tripKm = 0f
    var tripL = 0f
    var tripMs = 0L

    var temp: Int? = null
    var weatherTxt = ""
    var dtc: List<String>? = null

    fun obdFresh(): Boolean = obd != null && System.currentTimeMillis() - obdAt < 3000

    fun speed(): Float {
        val o = obd
        if (o != null && obdFresh() && o.speed >= 0) return o.speed.toFloat()
        return gpsSpeed
    }

    fun rpm(): Int {
        val o = obd
        return if (o != null && obdFresh()) o.rpm else -1
    }

    fun lph(): Float {
        val o = obd
        return if (o != null && obdFresh()) o.lph else -1f
    }

    fun instKml(): Float? {
        val l = lph()
        val s = speed()
        return if (l > 0.3f && s > 3f) s / l else null
    }

    fun avgKml(): Float? = if (tripL > 0.05f && tripKm > 0.2f) tripKm / tripL else null

    fun resetTrip() {
        tripKm = 0f
        tripL = 0f
        tripMs = 0L
    }
}
