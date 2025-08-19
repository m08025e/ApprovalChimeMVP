package com.example.approvalchime

import kotlin.math.*
import java.time.LocalDateTime
import kotlin.random.Random

data class Note(val freq: Double, val startMs: Int)

private val C_MAJOR_ARPEGGIO = doubleArrayOf(261.63, 329.63, 392.00, 523.25) // C4, E4, G4, C5

private fun pickConstrainedNotes(rng: Random): List<Note> {
    val num = if (rng.nextDouble() < 0.6) 2 else 3
    // Cメジャーアルペジオから常に選択し、明るく一貫したサウンドにする
    val pool = C_MAJOR_ARPEGGIO.toList()

    val chosen = pool.shuffled(rng).take(num)
    val pattern = rng.nextInt(3)
    val sorted = when (pattern) {
        0 -> chosen.sorted()           // 上昇
        1 -> chosen.sortedDescending() // 下降
        else -> chosen                 // アルペジオ風
    }
    // 開始時間をずらして心地よいアルペジオ効果を生み出す
    return sorted.mapIndexed { i, f -> Note(freq = f, startMs = i * 50) }
}

data class ToneSpec2(
    val durationMs: Int,
    val attackMs: Int,
    val decayMs: Int,
    val releaseMs: Int,
    val fmIndex: Double,
    val fmRatio: Double,
    val lowpassHz: Double
)

fun autoSpec2(now: LocalDateTime, rng: Random = Random.Default): ToneSpec2 {
    val duration = (400..1000).random(rng) // 0.4〜1.0s
    val night = now.hour >= 21 || now.hour <= 6
    return ToneSpec2(
        durationMs = duration,
        attackMs = if (night) 8 else 12,
        decayMs = (duration * 0.6).toInt(),
        releaseMs = (duration * 0.25).toInt(),
        fmIndex = if (night) 0.8 + rng.nextDouble() * 0.5 else 1.2 + rng.nextDouble() * 0.8,
        fmRatio = listOf(1.5, 2.0, 3.0).random(rng),
        lowpassHz = if (night) 4500.0 else 6000.0
    )
}

// 2オペFM＋ワンポールLPF、2〜3音を50ms間隔で順次重ねる
fun synthEMoneyLike(sampleRate: Int = 44100, targetDb: Double = -1.0): ShortArray {
    val rng = Random.Default
    val spec = autoSpec2(LocalDateTime.now(), rng)
    val notes = pickConstrainedNotes(rng)
    val n = spec.durationMs * sampleRate / 1000
    val out = DoubleArray(n)
    val atkN = (spec.attackMs * sampleRate / 1000.0).toInt().coerceAtLeast(1)
    val decN = (spec.decayMs * sampleRate / 1000.0).toInt().coerceAtLeast(1)
    val relN = (spec.releaseMs * sampleRate / 1000.0).toInt().coerceAtLeast(1)

    // LPF係数
    val rc = 1.0 / (2 * Math.PI * spec.lowpassHz)
    val alpha = 1.0 / (1.0 + rc * sampleRate)
    var lp = 0.0

    for (i in 0 until n) {
        var mix = 0.0
        for (note in notes) {
            val start = (note.startMs * sampleRate / 1000.0).toInt()
            if (i < start) continue
            val idx = i - start
            if (idx >= n - start) continue
            val tt = idx.toDouble() / sampleRate

            // ADSRっぽい包絡（A-D-R）
            val a = (idx.toDouble() / atkN).coerceIn(0.0, 1.0)
            val d = exp(-3.0 * idx / decN.toDouble())
            val r = if (idx > (n - start - relN)) ((n - start - idx).toDouble() / relN).coerceIn(0.0, 1.0) else 1.0
            val env = a * d * r

            // 軽いデチューン（±5セント）
            val detune = note.freq * (2.0).pow((rng.nextDouble() - 0.5) * 0.05 / 12.0)
            val mod = sin(2.0 * Math.PI * (detune * spec.fmRatio) * tt)
            val s = sin(2.0 * Math.PI * detune * tt + spec.fmIndex * mod)
            mix += s * env
        }
        // ローパスで耳当たり改善
        lp += alpha * (mix - lp)
        out[i] = lp
    }

    // 正規化
    val peak = out.maxOf { abs(it) }.coerceAtLeast(1e-9)
    val gain = 10.0.pow(targetDb / 20.0) / peak
    val pcm = ShortArray(n)
    for (i in 0 until n) {
        val v = (out[i] * gain).coerceIn(-1.0, 1.0)
        pcm[i] = (v * Short.MAX_VALUE).toInt().toShort()
    }
    return pcm
}