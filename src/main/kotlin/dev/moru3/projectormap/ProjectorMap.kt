package dev.moru3.projectormap

import com.google.gson.Gson
import dev.moru3.minepie.Executor.Companion.runTask
import dev.moru3.minepie.events.EventRegister.Companion.registerEvent
import dev.moru3.projectormap.event.PlayerMapGUIClickEvent
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class ProjectorMap: JavaPlugin(), Listener {
    override fun onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this)
        instance = this

        val gson = Gson()
        this.registerEvent<PlayerMapGUIClickEvent> { event ->
            instance.runTask { Bukkit.broadcastMessage("${event.relativeX}:${event.relativeY} ${event.player}")  }
        }
    }


    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(sender !is Player) { return true }
        Projector(MutableList(9) { MutableList(16) { MutableList(128) { MutableList(128) { 7.toByte() } } } })
            .send(sender,sender.location, BlockFace.EAST)
        return super.onCommand(sender, command, label, args)
    }

    companion object {
        lateinit var instance: ProjectorMap
    }
}