package dev.moru3.projectormap

import org.bukkit.plugin.java.JavaPlugin

class ProjectorMap: JavaPlugin() {
    override fun onEnable() {

    }

    init {
        instance = this
    }

    companion object {
        lateinit var instance: ProjectorMap
    }
}