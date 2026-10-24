package com.example.apknfc.qr

/**
 * Configurações do modo QR code.
 */
object QrConfig {

    // Só para TESTES: true força o modo QR mesmo em celulares que têm NFC.
    // Deixar false na versão final (o QR só aparece em celulares sem NFC).
    const val FORCE_QR_FOR_TESTING = false
}
