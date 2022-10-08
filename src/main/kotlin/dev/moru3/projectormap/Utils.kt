package dev.moru3.projectormap

import org.bukkit.map.MapPalette
import java.awt.Color

object Utils {
    // Color型をMap用のByteに変換する
    fun Color.toByte(): Byte = MapPalette.matchColor(this)
}