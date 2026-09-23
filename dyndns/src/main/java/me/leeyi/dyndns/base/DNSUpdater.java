package me.leeyi.dyndns.base;

import java.net.InetAddress;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.leeyi.dyndns.InvalidConfigException;

public abstract class DNSUpdater {
  public DNSUpdater(
    final Logger logger,
    final JavaPlugin parent
  ) {
    this.logger = logger;
    this.parentPlugin = parent;
  }

  public abstract @NotNull String getName();

  private int interval;
  public int getInterval() { return interval; }
  public DNSUpdater setInterval(final int interval) {
    this.interval = interval;
    return this;
  }

  private @Nullable BukkitTask updateTask = null;
  public final boolean isCancelled() {
    return this.updateTask == null || this.updateTask.isCancelled();
  }

  private @Nullable InetAddress lastRetrievedIp = null;
  public @Nullable InetAddress getLastRetrievedIp() { return this.lastRetrievedIp; }

  protected final @NotNull JavaPlugin parentPlugin;

  private final @NotNull Logger logger;
  public final @NotNull Logger getLogger() { return this.logger; }

  /**
   * Function that is called to obtain the current public IP address.
   */
  protected abstract @Nullable InetAddress getCurrentIp();

  /**
   * Function that is called to update the given DNS record with the IP
   * provided in the parameter
   * @return `true` if the operation succeeded, `false` otherwise.
   */
  protected abstract boolean updateDnsInternal(final InetAddress ip);

  /**
   * Main function that will:
   * 1. Get the current public IP
   * 2. Update the DNS record
   */
  public boolean updateDns() {
    final InetAddress ip = getCurrentIp();
    if (ip == null) return false;
    if (ip.equals(this.lastRetrievedIp)) {
      this.logger.info(String.format("Last retrieved IP was also %s, not updating...", ip.getHostAddress()));
      return true;
    }

    this.lastRetrievedIp = ip;
    return updateDnsInternal(ip);
  }

  /**
   * Cancels the running DNS task. If the task was not already running or was
   * already cancelled, returns `false`. Returns `true` otherwise.
   */
  public boolean cancelTask() {
    if (this.isCancelled()) return false;

    updateTask.cancel();
    return true;
  }

  public boolean startTask() {
    final BukkitRunnable runnable = new BukkitRunnable() {
      @Override
      public void run() { updateDns(); }
    };

    if (interval != -1) {
      if (isCancelled()) {
        this.updateTask = runnable.runTaskTimerAsynchronously(this.parentPlugin, 0L, interval * 20);
        return true;
      } else {
        return false;
      }
    } else {
      runnable.runTask(this.parentPlugin);
      return true;
    }
  }

  public ConfigurationSection toConfigurationSection() {
    final var config = new MemoryConfiguration();
    config.set("interval", this.getInterval());

    return config;
  }

  public final int stopCommandHandler(final CommandContext<CommandSourceStack> ctx) {
    final CommandSender sender = ctx.getSource().getSender();

    if (cancelTask()) {
      sender.sendPlainMessage(String.format("Automatic DNS updates stopped for %s.", this.getName()));
    } else {
      sender.sendPlainMessage(String.format("No Automatic DNS task running for %s.", this.getName()));
    }
    return 1;
  }

  public final int startCommandHandler(final CommandContext<CommandSourceStack> ctx) {
    final CommandSender sender = ctx.getSource().getSender();

    if (startTask()) {
      sender.sendPlainMessage(String.format("Updater task for %s was started.", this.getName()));
    } else {
      sender.sendPlainMessage(String.format("Updater task for %s is already running.", this.getName()));
    }

    return 1;
  }

  public final int lastCommandHandler(final CommandContext<CommandSourceStack> ctx) {
    final CommandSender sender = ctx.getSource().getSender();

    if (this.lastRetrievedIp == null) {
      sender.sendPlainMessage(String.format("%s has not yet retrieved IP address from %s", this.getName(), this.getLastRetrievedIp()));
    } else {
      sender.sendPlainMessage(String.format("Last retrieved IP address for %s was %s", this.getName(), this.lastRetrievedIp));
    }

    return 1;
  }

  // public final int reloadCommandHandler(final CommandContext<CommandSourceStack> ctx) {
  //   final List<String> configLoadErrors = loadConfig();
  //   final CommandSender sender = ctx.getSource().getSender();
    
  //   if (configLoadErrors.size() > 0) {
  //     sender.sendPlainMessage("Config failed to load");
  //     configLoadErrors.forEach(sender::sendPlainMessage);
  //   } else {
  //     startUpdateTask(runnable);
  //     sender.sendPlainMessage("Config reloaded.");
  //   }
  //   return 1;
  // }

  public final int statusCommandHandler(final CommandContext<CommandSourceStack> ctx) {
    final CommandSender sender = ctx.getSource().getSender();

    if (updateTask != null && !updateTask.isCancelled()) {
      sender.sendPlainMessage(String.format("Automatic DNS task is running for %s", this.getName()));
    } else {
      sender.sendPlainMessage(String.format("No automatic DNS task running for %s", this.getName()));
    }

    return 1;
  }

  protected final @NotNull String getStringFromSection(final String path, final ConfigurationSection config) throws InvalidConfigException {
    final String rawValue = config.getString(path);
    if (rawValue == null) {
      throw new InvalidConfigException(this, String.format("No value provided for %s", path));
    }

    return rawValue;
  }
}