package me.leeyi.dyndns.updaters;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.leeyi.dyndns.InvalidConfigException;
import me.leeyi.dyndns.base.HttpUpdater;

/**
 * Concrete implementation for Cloudflare DNS
 */
public class CloudflareUpdater extends HttpUpdater {
  public CloudflareUpdater(
    final @NotNull Logger logger,
    final @NotNull JavaPlugin parent,
    final @NotNull ConfigurationSection config
  ) throws InvalidConfigException {
    super(logger, parent, config);

    this.zoneId = this.getStringFromSection("zone_id", config);
    this.recordId = this.getStringFromSection("record_id", config);
    this.apiToken = this.getStringFromSection("api_token", config);
    this.proxied = config.getBoolean("proxied");
  }

  @Override
  public @NotNull String getName() { return "cloudflare"; }

  private String zoneId;
  public CloudflareUpdater setZoneId(final @NotNull String zoneId) {
    this.zoneId = zoneId;
    return this;
  }
  public String getZoneId() { return zoneId; }

  private String recordId;
  public CloudflareUpdater setRecordId(final @NotNull String recordId) {
    this.recordId = recordId;
    return this;
  }
  public String getRecordId() { return recordId; }

  private String apiToken;
  public CloudflareUpdater setApiToken(final @NotNull String apiToken) {
    this.apiToken = apiToken;
    return this;
  }
  public String getApiToken() { return apiToken; }

  /**
   * Set to `true` to tell Cloudflare to activate proxying
   */
  private boolean proxied;
  public boolean isProxied() { return proxied; }
  public CloudflareUpdater setProxied(final boolean proxied) {
    this.proxied = proxied;
    return this;
  }

  @Override
  protected @Nullable HttpRequest getHttpRequest(final InetAddress ip) {
    final String rawUri = String.format("https://api.cloudflare.com/client/v4/zones/%s/dns_records/%s", this.getZoneId(), this.getRecordId());
    final String payload = String.format("{\"type\":\"%s\",\"name\":\"%s\",\"content\":\"%s\",\"ttl\":%d,\"proxied\":%s}",
      ip instanceof Inet4Address ? "A" : "AAAA",
      this.getDomain(),
      ip.getHostAddress(),
      this.getTtl(),
      this.isProxied() ? "true" : "false"
    );

    try {
      final URI requestUri = new URI(rawUri);

      return HttpRequest.newBuilder(requestUri)
        .PUT(HttpRequest.BodyPublishers.ofString(payload))
        .header("Authorization", "Bearer " + this.getApiToken())
        .header("Content-Type", "application/json")    
        .build();
    } catch (URISyntaxException e) {
      getLogger().severe(String.format("Failed to format zone_id and record_id into a proper URI: %s", rawUri));
      return null;
    } 
  }
}
