package dev.moru3.projectormap

import dev.moru3.minepie.Executor.Companion.runTask
import dev.moru3.minepie.events.EventRegister.Companion.registerEvent
import dev.moru3.projectormap.event.PlayerMapGUIClickEvent
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import net.minecraft.server.v1_16_R3.*
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity.EnumEntityUseAction.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockFace.*
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapPalette
import java.awt.Color
import java.util.UUID


/**
 *
 * bytesで渡された画像を元にHxWのマップを作成します。
 * List<List<Byte>> = Height<Width<Color>>
 */
class Projector(val data: List<List<List<Byte>>>) {
    val uniqueId = UUID.randomUUID()
    // 画像をすべて表示するのに縦何枚のマップが必要かを計算
    val heightOfMap = data.size
    // 画像をすべて表示するのに横何枚の画像が必要かを計算
    val widthOfMap = ((data.getOrNull(0)?:throw IllegalArgumentException("illegal value in `bytes` argument.")).size)
    // マップの高さが何pxあるか
    val height = heightOfMap * 128
    // マップの幅が何pxあるか
    val width = widthOfMap * 128
    // マップに使用するアイテムを生成。
    val mapItems = MutableList(heightOfMap) { h -> MutableList(widthOfMap) { w ->
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
    }.map { CraftItemStack.asNMSCopy(it) } }

    val mapIds = mapItems.map { list -> list.map { CraftItemStack.getItemMeta(it) }.filterIsInstance(MapMeta::class.java).map { it.mapView?.id!! } }

    val flattedMapIds = mapIds.flatten()

    // プレイヤーに表示しているアイテムフレーム(日本語名忘れた)のエンティティID
    val playerFrames = mutableMapOf<Player,List<List<EntityItemFrame>>>()

    // entity id, pair<y,x>
    val itemFrameEntities = mutableMapOf<Int, Pair<Int, Int>>()

    init {
        ProjectorMap.instance.registerEvent<PlayerJoinEvent> { playerFrames.remove(it.player) }
    }

    fun send(player: Player, location: Location, blockFace: BlockFace) {
        // プレイヤーにまだアイテムフレームを表示してない場合はパケットで表示させる
        // >>> playerFrames.containsKey(player) >>>
        if(!playerFrames.containsKey(player)) {
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

            val pipelineName = "${player}-${uniqueId}-mapgui"
            val pipeline = (player as CraftPlayer).handle.playerConnection.networkManager.channel.pipeline()
            pipeline.addBefore("packet_handler", pipelineName, object: ChannelDuplexHandler() {
                override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                    when(msg) {
                        is PacketPlayInSetCreativeSlot -> {
                            if(flattedMapIds.contains((CraftItemStack.getItemMeta(msg.itemStack) as MapMeta).mapView?.id)) {
                                ProjectorMap.instance.runTask {
                                    player.inventory.setItem(msg.b()-36, null)
                                }
                            }
                        }
                        is PacketPlayInUseEntity -> {
                            val entityId = msg::class.java.getDeclaredField("a").also { it.isAccessible = true }.getInt(msg)
                            if(msg.c()!=EnumHand.MAIN_HAND&&msg.c()!=null) { return super.channelRead(ctx, msg) }
                            val relativeLoc = itemFrameEntities[entityId]?:return super.channelRead(ctx, msg)
                            val clickType: ClickType = when(msg.b()) {
                                INTERACT -> {
                                    if(player.isSneaking) { ClickType.SHIFT_RIGHT } else { ClickType.RIGHT }
                                }
                                ATTACK -> {
                                    if(player.isSneaking) { ClickType.SHIFT_LEFT } else { ClickType.LEFT }
                                }
                                else -> { return super.channelRead(ctx, msg) }
                            }
                            Bukkit.getPluginManager().callEvent(PlayerMapGUIClickEvent(player, this@Projector, relativeLoc.first, relativeLoc.second, clickType))
                        }
                    }
                    super.channelRead(ctx, msg)
                }
            })


            playerFrames[player] = MutableList(heightOfMap) { h -> MutableList(widthOfMap) { w ->
                EntityItemFrame((location.world as CraftWorld).handle, BlockPosition(location.x+(w*wRelativeX),location.y+(hRelativeY*h),location.z+((hRelativeZ*h)+(w*wRelativeZ))), direction)
                    .also { itemFrameEntities[it.id] = h to w }
                    .also { it.direction = direction }
                    .also { it.setItem(mapItems[h][w], false, false) }
            } }

            playerFrames[player]?.flatten()?.forEach {
                player.handle.playerConnection.sendPacket(PacketPlayOutSpawnEntity(it, direction.c()))
                player.handle.playerConnection.sendPacket(PacketPlayOutEntityMetadata(it.id, it.dataWatcher, false))
            }
        }
        // <<< playerFrames.containsKey(player) <<<

        // 全マップに画像を表示するパケットを作成し、二重リストになっているため回しやすいように一次元化
        val packets = data.mapIndexed { h, line -> line.mapIndexed { w, bytes -> PacketPlayOutMap(mapIds[h][w], 0, true, false, listOf(), bytes.toByteArray(), 0, 0, 128, 128) } }.flatten()
        // 生成したパケットをすべて生成
        packets.forEach { (player as CraftPlayer).handle.playerConnection.sendPacket(it) }
    }

    companion object {

        /**
         * @param bytes List<List<List<List>>>> = MapY<MapX<y<x>>>
         */
        operator fun invoke(bytes: List<List<List<List<Byte>>>>): Projector {
            return Projector(bytes.map { it.map { it.flatten() } })
        }

        /**
         * @param bytes 画像データ
         * @param backgroundColor 画像の余白部分の背景
         */
        operator fun invoke(bytes: List<List<Byte>>, backgroundColor: Byte = MapPalette.matchColor(Color.WHITE)): Projector {
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
            val base = MutableList(128) { MutableList(128) { backgroundColor } }
            // flattenで平らにしてからすべてのマップにin
            // List<List<Byte>> = Height<Width<Colors>>
            val data = MutableList(heightOfMap) { MutableList(widthOfMap) { base.flatten().toMutableList() } }

            bytes.forEachIndexed { y, bytes2 -> bytes2.forEachIndexed { x, byte ->
                val mapY = y/128
                val mapX = x/128
                val offsetY = mapY*128
                val offsetX = mapX*128
                data[mapY][mapX][((y-offsetY)*128)+(x-offsetX)] = byte
            } }

            return Projector(data)
        }
    }
}