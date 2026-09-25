package me.leeyi.dyndns.base;

import java.net.InetAddress;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public abstract boolean updateDns(final InetAddress ip);

  private @Nullable InetAddress lastRetrievedIp = null;
  public @Nullable InetAddress getLastRetrievedIp() { return this.lastRetrievedIp; }

  protected final @NotNull JavaPlugin parentPlugin;

  private final @NotNull Logger logger;
  public final @NotNull Logger getLogger() { return this.logger; }

  private boolean enabled;
  public boolean isEnabled() { return this.enabled; }
  public DNSUpdater setEnabled(final boolean value) {
    this.enabled = value;
    return this;
  }

  protected final @NotNull String getStringFromSection(final String path, final ConfigurationSection config) throws InvalidConfigException {
    final String rawValue = config.getString(path);
    if (rawValue == null) {
      throw new InvalidConfigException(this, String.format("No value provided for %s", path));
    }

    return rawValue;
  }
}