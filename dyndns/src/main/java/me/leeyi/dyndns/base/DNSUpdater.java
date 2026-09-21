package me.leeyi.dyndns.base;

import java.net.InetAddress;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      this.logger.info(String.format("Last retrieved IP was also %s, not updating...", ip));
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

  public void startTask() {
    final BukkitRunnable runnable = new BukkitRunnable() {
      @Override
      public void run() { updateDns(); }
    };

    if (interval != -1) {
      if (isCancelled()) {
        this.updateTask = runnable.runTaskTimerAsynchronously(this.parentPlugin, 0L, interval * 20);
      }
    } else {
      runnable.runTask(this.parentPlugin);
    }
  }

  public ConfigurationSection toConfigurationSection() {
    final var config = new MemoryConfiguration();
    config.set("interval", this.getInterval());

    return config;
  }

  public List<String> fromConfigurationSection(final ConfigurationSection config) {
    this.setInterval(config.getInt("interval", -1));
    return List.of();
  }
}