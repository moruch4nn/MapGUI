package dev.moru3.projectormap

import dev.moru3.minepie.events.EventRegister.Companion.registerEvent
import net.minecraft.server.v1_16_R3.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockFace.*
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapPalette
import java.awt.Color
import kotlin.concurrent.thread


/**
 *
 * bytesで渡された画像を元にHxWのマップを作成します。
 * List<List<Byte>> = Height<Width<Color>>
 */
class Projector(val bytes: List<List<Byte>>, val backgroundColor: Color = Color.WHITE) {
    // 画像をすべて表示するのに縦何枚のマップが必要かを計算
    val heightOfMap = (bytes.size/128)+(minOf(1,bytes.size%128))
    // 画像をすべて表示するのに横何枚の画像が必要かを計算
    val widthOfMap = ((bytes.getOrNull(0)?:throw IllegalArgumentException("illegal value in `bytes` argument.")).size/128)+(minOf(1,bytes[0].size%128))
    // マップの高さが何pxあるか
    val height = heightOfMap * 128
    // マップの幅が何pxあるか
    val width = widthOfMap * 128
    // 上から何px離すと画像が中心に来るかを計算
    val topOffset = (height-bytes.size)/2
    // 左から何px離すと画像が中心に来るかを計算
    val leftOffset = (width-bytes[0].size)/2


    // 128x128の真っ白のキャンバスを作成する
    val base = MutableList(128) { MutableList(128) { MapPalette.matchColor(backgroundColor) } }
    // flattenで平らにしてからすべてのマップにin
    // List<List<Byte>> = Height<Width<Colors>>
    val data = MutableList(heightOfMap) { MutableList(widthOfMap) { base.flatten().toMutableList() } }

    // マップに使用するアイテムを生成。
    val mapItems = MutableList(heightOfMap) { MutableList(widthOfMap) {
        ItemStack(Material.FILLED_MAP).apply {
            itemMeta = (itemMeta as MapMeta).also { mapMeta ->
                mapMeta.mapView = Bukkit.createMap(Bukkit.getWorlds()[0]).also { mapView ->
                    mapView.isLocked = true
                    mapView.isTrackingPosition = false
                    mapView.isUnlimitedTracking = false
                    mapView.renderers.forEach(mapView::removeRenderer)
                    mapMeta.mapView = mapView
                } }
        }
    } }

    // マップID
    val mapIds = mapItems.map { it.map { (it.itemMeta as MapMeta).mapView!!.id } }

    // プレイヤーに表示しているアイテムフレーム(日本語名忘れた)のエンティティID
    val playerFrames = mutableMapOf<Player,List<List<EntityItemFrame>>>()

    init {
        bytes.forEachIndexed { y, bytes -> bytes.forEachIndexed { x, byte ->
            val mapY = y/128
            val mapX = x/128
            val offsetY = mapY*128
            val offsetX = mapX*128
            data[mapY][mapX][((y-offsetY)*128)+(x-offsetX)] = byte
        } }

        ProjectorMap.instance.registerEvent<PlayerJoinEvent> { playerFrames.remove(it.player) }
    }

    fun send(player: Player, location: Location, blockFace: BlockFace) {
        // プレイヤーにまだアイテムフレームを表示してない場合はパケットで表示させる
        if(playerFrames.containsKey(player)) {
            var wRelativeX = 0
            var wRelativeZ = 0
            var hRelativeY = 0
            var hRelativeZ = 0
            val direction: EnumDirection
            when(blockFace) {
                NORTH -> {
                    direction = EnumDirection.NORTH
                    hRelativeY = 1
                    wRelativeX = -1
                }
                EAST -> {
                    direction = EnumDirection.EAST
                    hRelativeY = 1
                    wRelativeZ = -1
                }
                SOUTH -> {
                    direction = EnumDirection.SOUTH
                    hRelativeY = 1
                    wRelativeX = 1
                }
                WEST -> {
                    direction = EnumDirection.WEST
                    hRelativeY = 1
                    wRelativeZ = 1
                }
                UP -> {
                    direction = EnumDirection.UP
                    hRelativeZ = 1
                    wRelativeX = 1
                }
                DOWN -> {
                    direction = EnumDirection.DOWN
                    hRelativeZ = -1
                    wRelativeX = 1
                }
                else -> { return }
            }
            playerFrames[player] = MutableList(heightOfMap) { h -> MutableList(widthOfMap) { w ->
                EntityItemFrame((location.world as CraftWorld).handle, BlockPosition(location.x+(w*wRelativeX),location.y+(hRelativeY*h),location.z+((hRelativeZ*h)+(w*wRelativeZ))), direction)
            } }
        }
        // 全マップに画像を表示するパケットを作成し、二重リストになっているため回しやすいように一次元化
        val packets = data.mapIndexed { h, line -> line.mapIndexed { w, bytes -> PacketPlayOutMap(playerFrames[player]!![h][w].id, 0, true, false, listOf(), bytes.toByteArray(), 0, 0, 128, 128) } }.flatten()
        packets.forEach { (player as CraftPlayer).handle.playerConnection.sendPacket(it) }
    }
}