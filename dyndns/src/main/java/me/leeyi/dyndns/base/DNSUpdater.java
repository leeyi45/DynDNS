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
    final @NotNull Logger logger,
    final @NotNull JavaPlugin parent,
    final @NotNull ConfigurationSection config
  ) throws InvalidConfigException {
    this.logger = logger;
    this.parentPlugin = parent;
    this.enabled = config.getBoolean("enabled", true);

    this.domain = this.getStringFromSection("domain", config);
    this.ttl = config.getInt("ttl", 1);

    if (ttl <= 0) {
      throw new InvalidConfigException(this, String.format("TTL Value was negative: %d", ttl));
    }
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

  /**
   * Domain to be updated, i.e `domain.toupdate.com`
   */
  private String domain;

  public DNSUpdater setDomain(final @NotNull String domain) {
    this.domain = domain;
    return this;
  }
  public String getDomain() { return domain; }

  private int ttl;
  public int getTtl() {
    return ttl;
  }
  public DNSUpdater setTtl(final int ttl) {
    this.ttl = ttl;
    return this;
  }

  protected final @NotNull String getStringFromSection(final String path, final @NotNull ConfigurationSection config) throws InvalidConfigException {
    final String rawValue = config.getString(path);
    if (rawValue == null) {
      throw new InvalidConfigException(this, String.format("No value provided for %s", path));
    }

    return rawValue;
  }
}