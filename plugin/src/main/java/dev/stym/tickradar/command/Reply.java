package dev.stym.tickradar.command;

import dev.stym.tickradar.schedule.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

final class Reply {

    private final CommandSender audience;
    private final Location blockLocation;
    private final TaskScheduler scheduler;

    private Reply(CommandSender audience, Location blockLocation, TaskScheduler scheduler) {
        this.audience = audience;
        this.blockLocation = blockLocation;
        this.scheduler = scheduler;
    }

    static Reply to(CommandSender sender, CommandSender console, TaskScheduler scheduler, Component rconNotice) {
        if (sender instanceof RemoteConsoleCommandSender) {
            sender.sendMessage(rconNotice);
            return new Reply(console, null, scheduler);
        }
        if (sender instanceof BlockCommandSender block) {
            return new Reply(sender, block.getBlock().getLocation(), scheduler);
        }
        return new Reply(sender, null, scheduler);
    }

    void send(Component message) {
        if (audience instanceof Player player) {
            scheduler.runForPlayer(player, () -> player.sendMessage(message));
        } else if (blockLocation != null) {
            scheduler.runAtLocation(blockLocation, () -> audience.sendMessage(message));
        } else {
            scheduler.runGlobal(() -> audience.sendMessage(message));
        }
    }
}
