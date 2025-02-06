package org.winlogon.whisperchat

import org.bukkit.Bukkit
import org.bukkit.command.{Command, CommandExecutor, CommandSender}
import org.bukkit.entity.Player
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin

import java.util.UUID
import scala.collection.concurrent.TrieMap

class WhisperChatPlugin extends JavaPlugin {
  private val activeDMs = TrieMap.empty[UUID, UUID]
  private val lastSenders = TrieMap.empty[UUID, UUID]
  private var isFolia: Boolean = false

  override def onEnable(): Unit = {
    isFolia = checkFolia()
    getCommand("dm").setExecutor(new DMCommandExecutor(this))
    getCommand("r").setExecutor(new ReplyCommandExecutor(this))
    Bukkit.getPluginManager.registerEvents(new ChatListener(this), this)
  }

  private def checkFolia(): Boolean = {
    try {
      Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
      true
    } catch {
      case _: ClassNotFoundException => false
    }
  }

  def getActiveDMs: TrieMap[UUID, UUID] = activeDMs
  def getLastSenders: TrieMap[UUID, UUID] = lastSenders
  def isServerFolia: Boolean = isFolia
}

class DMCommandExecutor(plugin: WhisperChatPlugin) extends CommandExecutor {
  private val activeDMs = plugin.getActiveDMs
  private val lastSenders = plugin.getLastSenders

  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
    if (!sender.isInstanceOf[Player]) {
      sender.sendMessage("§cOnly players can use this command.")
      return true
    }

    val player = sender.asInstanceOf[Player]

    if (args.isEmpty) {
      player.sendMessage("§7Usage: §3/dm §2<username|leave>")
      return true
    }

    if (args(0).equalsIgnoreCase("leave")) {
      activeDMs.remove(player.getUniqueId)
      player.sendMessage("§7You have §3left§7 the DM. Chat is now §2global§7.")
      return true
    }

    val target = Bukkit.getPlayer(args(0))
    if (target == null) {
      player.sendMessage(s"§cError: §7Player §3${args(0)}§7 not found or offline.")
      return true
    }

    if (target == player) {
      player.sendMessage("§cnotepad exists mate")
      return true
    }

    activeDMs.put(player.getUniqueId, target.getUniqueId)
    lastSenders.put(target.getUniqueId, player.getUniqueId)
    player.sendMessage(s"§7Now DMing §3${target.getName}§7. Type normally in chat to send private messages.")
    true
  }
}

class ReplyCommandExecutor(plugin: WhisperChatPlugin) extends CommandExecutor {
  private val lastSenders = plugin.getLastSenders

  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
    if (!sender.isInstanceOf[Player]) {
      sender.sendMessage("§cOnly players can use this command.")
      return true
    }

    val player = sender.asInstanceOf[Player]

    if (args.isEmpty) {
      player.sendMessage("§7Usage: §3/r §2<message>")
      return true
    }

    val lastSender = lastSenders.get(player.getUniqueId)

    lastSender match {
      case Some(lastSenderUuid) =>
        val target = Bukkit.getPlayer(lastSenderUuid)
        if (target == null || !target.isOnline) {
          player.sendMessage("§7The player you're replying to is §3offline.")
          lastSenders.remove(player.getUniqueId)
          true
        } else {
          val message = args.mkString(" ")
          target.sendMessage(s"§7${player.getName} whispers to you: §f$message")
          player.sendMessage(s"§7You whisper to §6${target.getName}§7: §f$message")
          lastSenders.put(target.getUniqueId, player.getUniqueId)
          true
        }
      case None =>
        player.sendMessage("§cYou have no one to reply to.")
        true
    }
  }
}

class ChatListener(plugin: WhisperChatPlugin) extends Listener {
  private val activeDMs = plugin.getActiveDMs
  private val lastSenders = plugin.getLastSenders
  private val isFolia = plugin.isServerFolia

  @EventHandler(priority = EventPriority.LOWEST)
  def onChat(event: AsyncPlayerChatEvent): Unit = {
    val player = event.getPlayer
    activeDMs.get(player.getUniqueId).foreach { targetUuid =>
      event.setCancelled(true)
      val message = event.getMessage
      val senderUuid = player.getUniqueId

      // Create a proper Java Runnable instance
      val runnable = new Runnable {
        override def run(): Unit = {
          val sender = Bukkit.getPlayer(senderUuid)
          if (sender == null || !sender.isOnline) return

          val target = Bukkit.getPlayer(targetUuid)
          if (target == null || !target.isOnline) {
            sender.sendMessage("§cPlayer is offline. Returning to global chat.")
            activeDMs.remove(senderUuid)
          } else {
            target.sendMessage(s"§7${sender.getName} whispers to you: §f$message")
            sender.sendMessage(s"§7You whisper to §6${target.getName}§7: §f$message")
            lastSenders.put(target.getUniqueId, senderUuid)
          }
        }
      }

      if (isFolia) {
        player.getScheduler.run(plugin, _ => runnable.run(), null)
      } else {
        Bukkit.getScheduler.runTask(plugin, runnable)
      }
    }
  }
}
