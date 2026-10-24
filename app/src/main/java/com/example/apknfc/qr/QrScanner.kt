package com.example.apknfc.qr

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Abre o leitor de QR code do Google (Google Code Scanner).
 * Ele tem tela própria e não exige permissão de câmera, mas precisa do
 * Google Play Services no aparelho.
 */
object QrScanner {

    fun scan(
        context: Context,
        onResult: (String) -> Unit,   // texto lido do QR
        onCancel: () -> Unit,         // usuário fechou o leitor
        onError: (String) -> Unit     // falha ao abrir ou ler
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) onError("QR code vazio.") else onResult(raw)
            }
            .addOnCanceledListener { onCancel() }
            .addOnFailureListener { e ->
                onError(
                    "Não foi possível abrir o leitor de QR code. " +
                            "Verifique a conexão e o Google Play Services. (${e.message})"
                )
            }
    }
}
