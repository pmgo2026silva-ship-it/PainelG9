package br.painel.g9

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class ObdData {
    @Volatile var rpm = -1
    @Volatile var speed = -1
    @Volatile var coolant = -999
    @Volatile var load = -1
    @Volatile var throttle = -1
    @Volatile var iat = -999
    @Volatile var volt = -1f
    @Volatile var lph = -1f
}

/**
 * Leitura (somente leitura!) de adaptador ELM327 por Bluetooth clássico (SPP) ou Wi-Fi.
 * Nunca envia comandos de escrita/apagar códigos.
 */
class Obd(
    private val onState: (String) -> Unit,
    private val onData: (ObdData) -> Unit,
    private val onDtc: (List<String>) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var dtcRequested = false
    private var thread: Thread? = null
    private var sock: Closeable? = null
    private var inp: InputStream? = null
    private var out: OutputStream? = null

    var calib = 1.0f
    val data = ObdData()

    fun start(btDevice: BluetoothDevice?, wifi: String?) {
        stop()
        running = true
        val t = Thread { loop(btDevice, wifi) }
        t.isDaemon = true
        thread = t
        t.start()
    }

    fun stop() {
        running = false
        close()
        thread?.interrupt()
        thread = null
    }

    fun requestDtc() {
        dtcRequested = true
    }

    private fun state(s: String) {
        main.post { onState(s) }
    }

    private fun close() {
        try { sock?.close() } catch (e: Exception) { }
        sock = null
        inp = null
        out = null
    }

    private fun loop(dev: BluetoothDevice?, wifi: String?) {
        while (running) {
            try {
                state("Conectando…")
                connect(dev, wifi)
                initElm()
                state("Conectado")
                poll()
            } catch (e: Exception) {
                if (!running) return
                state("Sem conexão (tentando de novo)")
            }
            close()
            try {
                var i = 0
                while (running && i < 50) {
                    Thread.sleep(100)
                    i++
                }
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(dev: BluetoothDevice?, wifi: String?) {
        if (dev != null) {
            try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (e: Exception) { }
            val s: BluetoothSocket = try {
                val a = dev.createRfcommSocketToServiceRecord(SPP)
                a.connect()
                a
            } catch (e: Exception) {
                val b = dev.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(dev, 1) as BluetoothSocket
                b.connect()
                b
            }
            sock = s
            inp = s.inputStream
            out = s.outputStream
        } else if (wifi != null) {
            val parts = wifi.split(":")
            val host = parts[0].trim()
            val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 35000
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 4000)
            sock = s
            inp = s.getInputStream()
            out = s.getOutputStream()
        } else {
            throw IOException("sem adaptador")
        }
    }

    private fun initElm() {
        cmd("ATZ", 3500)
        cmd("ATE0")
        cmd("ATL0")
        cmd("ATS0")
        cmd("ATH0")
        cmd("ATSP0")
        val r = cmd("0100", 12000).uppercase()
        if (r.contains("UNABLE") || r.contains("ERROR") || r.isEmpty()) throw IOException("ecu")
    }

    private fun cmdRaw(c: String, to: Long = 2500): String {
        val o = out ?: throw IOException("fechado")
        val i = inp ?: throw IOException("fechado")
        while (i.available() > 0) i.read()
        o.write((c + "\r").toByteArray())
        o.flush()
        val sb = StringBuilder()
        val end = System.currentTimeMillis() + to
        while (System.currentTimeMillis() < end) {
            if (i.available() > 0) {
                val b = i.read()
                if (b < 0) throw IOException("eof")
                val ch = b.toChar()
                if (ch == '>') break
                sb.append(ch)
            } else {
                Thread.sleep(8)
            }
        }
        return sb.toString()
    }

    private fun cmd(c: String, to: Long = 2500): String =
        cmdRaw(c, to).replace("\r", " ").replace("\n", " ").replace("SEARCHING...", "").trim()

    private fun pid(p: String): IntArray? {
        val r = cmd("01$p").uppercase().replace(" ", "")
        if (r.isEmpty()) {
            emptyCount++
            if (emptyCount > 6) throw IOException("sem resposta")
            return null
        }
        emptyCount = 0
        if (r.contains("NODATA") || r.contains("UNABLE") || r.contains("ERROR") ||
            r.contains("?") || r.contains("STOPPED")
        ) return null
        val i = r.indexOf("41$p")
        if (i < 0) return null
        val h = r.substring(i + 4)
        val n = h.length / 2
        return IntArray(n) { h.substring(it * 2, it * 2 + 2).toIntOrNull(16) ?: 0 }
    }

    private var emptyCount = 0

    private fun poll() {
        var n = 0
        var mafFail = 0
        while (running) {
            pid("0C")?.let { if (it.size >= 2) data.rpm = (it[0] * 256 + it[1]) / 4 }
            pid("0D")?.let { if (it.isNotEmpty()) data.speed = it[0] }

            if (n % 3 == 0) {
                pid("05")?.let { if (it.isNotEmpty()) data.coolant = it[0] - 40 }
                pid("04")?.let { if (it.isNotEmpty()) data.load = it[0] * 100 / 255 }
                pid("11")?.let { if (it.isNotEmpty()) data.throttle = it[0] * 100 / 255 }
                pid("0F")?.let { if (it.isNotEmpty()) data.iat = it[0] - 40 }
                val v = cmd("ATRV").uppercase().replace("V", "").trim().replace(",", ".")
                v.toFloatOrNull()?.let { data.volt = it }
            }

            // consumo: tenta MAF; se o carro não tiver, estima por pressão do coletor (MAP)
            var airGs = -1f
            if (mafFail < 3) {
                val m = pid("10")
                if (m != null && m.size >= 2) airGs = (m[0] * 256 + m[1]) / 100f else mafFail++
            }
            if (airGs < 0f) {
                val map = pid("0B")
                if (map != null && map.isNotEmpty() && data.rpm > 0) {
                    val t = (if (data.iat > -100) data.iat else 30) + 273.15f
                    val rho = map[0] * 1000f / (287f * t)
                    airGs = rho * 0.001997f * (data.rpm / 120f) * 0.85f * 1000f
                }
            }
            if (airGs >= 0f) {
                data.lph = if (data.rpm <= 0) 0f else (airGs / AFR) / DENS * 3600f * calib
            }

            if (dtcRequested) {
                dtcRequested = false
                val raw = cmdRaw("03", 5000)
                val list = parseDtc(raw)
                main.post { onDtc(list) }
            }

            main.post { onData(data) }
            n++
        }
    }

    companion object {
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val AFR = 13.5f   // relação ar/combustível média (gasolina brasileira com etanol)
        private const val DENS = 750f   // g/L

        fun parseDtc(raw: String): List<String> {
            val res = ArrayList<String>()
            for (line in raw.split("\r", "\n")) {
                var l = line.uppercase().replace(" ", "").replace("SEARCHING...", "")
                if (!l.startsWith("43")) continue
                l = l.substring(2)
                if ((l.length / 2) % 2 == 1) l = l.substring(2)
                var k = 0
                while (k + 4 <= l.length) {
                    val w = l.substring(k, k + 4)
                    k += 4
                    if (w == "0000") continue
                    val v = w.toIntOrNull(16) ?: continue
                    val t = "PCBU"[(v shr 14) and 3]
                    val d1 = (v shr 12) and 3
                    val rest = (v and 0x0FFF).toString(16).uppercase().padStart(3, '0')
                    res.add("$t$d1$rest")
                }
            }
            return res.distinct()
        }

        private val DESC = mapOf(
            "P0016" to "Correlação virabrequim x comando de válvulas",
            "P0101" to "Sensor de fluxo de ar (MAF) fora de faixa",
            "P0106" to "Sensor de pressão do coletor (MAP) fora de faixa",
            "P0112" to "Sensor de temperatura do ar (IAT) com sinal baixo",
            "P0113" to "Sensor de temperatura do ar (IAT) com sinal alto",
            "P0117" to "Sensor de temperatura do motor com sinal baixo",
            "P0118" to "Sensor de temperatura do motor com sinal alto",
            "P0122" to "Sensor de posição da borboleta com sinal baixo",
            "P0133" to "Sonda lambda lenta (banco 1, sensor 1)",
            "P0135" to "Aquecedor da sonda lambda (banco 1, sensor 1)",
            "P0141" to "Aquecedor da sonda lambda (banco 1, sensor 2)",
            "P0171" to "Mistura pobre (banco 1)",
            "P0172" to "Mistura rica (banco 1)",
            "P0300" to "Falha de ignição em vários cilindros",
            "P0301" to "Falha de ignição no cilindro 1",
            "P0302" to "Falha de ignição no cilindro 2",
            "P0303" to "Falha de ignição no cilindro 3",
            "P0304" to "Falha de ignição no cilindro 4",
            "P0325" to "Sensor de detonação com defeito",
            "P0335" to "Sensor de posição do virabrequim",
            "P0340" to "Sensor de posição do comando de válvulas",
            "P0420" to "Eficiência do catalisador abaixo do esperado",
            "P0441" to "Fluxo de purga do sistema evaporativo",
            "P0442" to "Pequeno vazamento no sistema evaporativo",
            "P0455" to "Grande vazamento no sistema evaporativo",
            "P0500" to "Sensor de velocidade do veículo",
            "P0506" to "Marcha lenta abaixo do esperado",
            "P0507" to "Marcha lenta acima do esperado"
        )

        fun describe(code: String): String = DESC[code] ?: "Consulte a tabela do fabricante (Honda)"
    }
}
