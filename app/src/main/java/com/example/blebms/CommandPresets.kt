package com.example.blebms

/**
 * Placeholder command bytes for the ON / OFF preset buttons.
 *
 * These are NOT real values yet — BAT-BMS's protocol isn't public.
 * Once you capture the real bytes (see README.md, "Finding the real
 * command bytes"), replace ON_HEX / OFF_HEX below and rebuild.
 *
 * Format: hex string, spaces optional, e.g. "AA 01 55" or "AA0155".
 */
object CommandPresets {
    const val ON_HEX = "AA0101"
    const val OFF_HEX = "AA0100"
}
