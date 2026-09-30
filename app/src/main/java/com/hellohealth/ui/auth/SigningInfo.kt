package com.hellohealth.ui.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Reads the SHA-1 fingerprint of the certificate the *running* build is signed with, formatted the
 * way `keytool` and the Google Cloud console show it (colon-separated uppercase hex). This is what
 * must be registered in Cloud project 637347574786 for Google Sign-In to work; logging it on a
 * DEVELOPER_ERROR makes the misconfiguration self-diagnosing instead of a keytool hunt.
 *
 * Returns null on any failure — this is diagnostic only and must never throw into the UI.
 */
internal fun currentSigningSha1(context: Context): String? = runCatching {
    val pm = context.packageManager
    val pkg = context.packageName
    val signatures: Array<android.content.pm.Signature>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return@runCatching null
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }

    signatures?.firstOrNull()?.let { sig ->
        val digest = MessageDigest.getInstance("SHA-1").digest(sig.toByteArray())
        toColonHex(digest)
    }
}.getOrNull()

/** Formats a digest as colon-separated uppercase hex, e.g. "A1:B2:C3:…" — the keytool/console form. */
internal fun toColonHex(bytes: ByteArray): String =
    bytes.joinToString(":") { "%02X".format(it) }
