package com.example.approvalchime

import android.app.NotificationChannel
import android.Manifest
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun savePcmAsWavToMediaStore(context: Context, displayName: String, pcm: ShortArray, sampleRate: Int = 44100): Uri {
    val bytes = toWavBytes(pcm, sampleRate)
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, "$displayName.wav")
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Notifications/")
        put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Audio.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("MediaStore insert failed")
    resolver.openOutputStream(uri)?.use { it.write(bytes) }
    if (Build.VERSION.SDK_INT >= 29) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
    }
    return uri
}

private fun toWavBytes(pcm: ShortArray, sampleRate: Int): ByteArray {
    val numChannels = 1
    val bitsPerSample = 16
    val subchunk2Size = pcm.size * numChannels * bitsPerSample / 8
    val byteRate = sampleRate * numChannels * bitsPerSample / 8
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + subchunk2Size); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(numChannels.toShort())
        putInt(sampleRate); putInt(byteRate); putShort((numChannels * bitsPerSample / 8).toShort()); putShort(bitsPerSample.toShort())
        put("data".toByteArray()); putInt(subchunk2Size)
    }.array()
    val body = ByteArray(pcm.size * 2)
    var j = 0
    for (s in pcm) {
        body[j++] = (s.toInt() and 0xFF).toByte()
        body[j++] = ((s.toInt() shr 8) and 0xFF).toByte()
    }
    return header + body
}

fun createChannelWithSound(context: Context, channelId: String, name: String, soundUri: Uri) {
    val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    val ch = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
        setSound(soundUri, attrs)
        enableVibration(true)
    }
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(ch)
}

fun postTestNotification(context: Context, channelId: String) {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        // MainActivityでリクエストはしているが、ユーザーが拒否した場合などは何もしない
        return
    }
    val n = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("新しい承認音を適用しました")
        .setContentText("この通知が鳴った音が、今回の生成サウンドです。")
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n)
}