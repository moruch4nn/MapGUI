package dev.moru3.projectormap.event

import dev.moru3.projectormap.Projector
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.inventory.ClickType

/**
 * @param player 対象のプレイヤー
 * @param projector クリックしたスクリーン
 * @param relativeX クリックしたマップの相対位置(X)。基本的にマップの左下が(0,0)です。(縦,横)
 * @param relativeY クリックしたマップの相対位置(Y)。基本的にマップの左下が(0,0)です。(縦,横)
 */
class PlayerMapGUIClickEvent(val player: Player, val projector: Projector, val relativeX: Int, val relativeY: Int, val clickType: ClickType): Event(true)  {

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }
}