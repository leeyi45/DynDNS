package me.leeyi.dyndns.base;

import java.net.InetAddress;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import me.leeyi.dyndns.InvalidConfigException;

public abstract class DNSUpdater {
  public DNSUpdater(
    final @NotNull JavaPlugin parent,
    final @NotNull ConfigurationSection config
  ) throws InvalidConfigException {
    this.parentPlugin = parent;
    this.enabled = config.getBoolean("enabled", true);

    this.domain = this.getStringFromSection("domain", config);
    this.ttl = config.getInt("ttl", 1);
  }

  public abstract @NotNull String getName();

  /**
   * Function that is called to actually update the DNS record
   */
  public abstract boolean updateDns(final InetAddress ip);

  private final @NotNull JavaPlugin parentPlugin;
  public final @NotNull Logger getLogger() { return parentPlugin.getLogger(); }

  private boolean enabled;
  public boolean isEnabled() { return this.enabled; }
  public DNSUpdater setEnabled(final boolean value) {
    this.enabled = value;
    return this;
  }

  /**
   * Domain to be updated, i.e `domain.toupdate.com`
   */
  private @NotNull String domain;
  public DNSUpdater setDomain(final @NotNull String domain) {
    this.domain = domain;
    return this;
  }
  public @NotNull String getDomain() { return domain; }

  /**
   * DNS Time To Live value (in seconds)
   */
  private int ttl;
  public int getTtl() {
    return ttl;
  }
  public DNSUpdater setTtl(final int ttl) {
    this.ttl = ttl;
    return this;
  }

  protected final @NotNull String getStringFromSection(final @NotNull String path, final @NotNull ConfigurationSection config) throws InvalidConfigException {
    final String rawValue = config.getString(path);
    if (rawValue == null) {
      throw new InvalidConfigException(this, String.format("No value provided for %s", path));
    }

    return rawValue;
  }
}