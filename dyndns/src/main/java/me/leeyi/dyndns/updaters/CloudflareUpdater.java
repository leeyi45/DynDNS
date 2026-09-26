package me.leeyi.dyndns.updaters;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.leeyi.dyndns.InvalidConfigException;
import me.leeyi.dyndns.base.DNSUpdater;

/**
 * Concrete implementation for Cloudflare DNS
 */
public class CloudflareUpdater extends DNSUpdater {
  public CloudflareUpdater(
    final @NotNull Logger logger,
    final @NotNull JavaPlugin parent,
    final @Nullable ConfigurationSection config
  ) throws InvalidConfigException {
    super(logger, parent);

    this.domain = this.getStringFromSection("domain", config);
    this.zoneId = this.getStringFromSection("zone_id", config);
    this.recordId = this.getStringFromSection("record_id", config);
    this.apiToken = this.getStringFromSection("api_token", config);
    this.ttl = config.getInt("ttl", 1);
    this.proxied = config.getBoolean("proxied");
  }

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  public @NotNull String getName() { return "cloudflare"; }

  /**
   * Domain to be updated, i.e `domain.toupdate.com`
   */
  private String domain;

  public void setDomain(final @NotNull String domain) {
    this.domain = domain;
  }

  public String getDomain() {
    return domain;
  }

  private String zoneId;
  public void setZoneId(final @NotNull String zoneId) {
    this.zoneId = zoneId;
  }

  public String getZoneId() {
    return zoneId;
  }

  private String recordId;
  public void setRecordId(final @NotNull String recordId) {
    this.recordId = recordId;
  }

  public String getRecordId() {
    return recordId;
  }

  private String apiToken;
  public void setApiToken(final @NotNull String apiToken) {
    this.apiToken = apiToken;
  }

  public String getApiToken() {
    return apiToken;
  }

  private int ttl;
  public int getTtl() {
    return ttl;
  }

  public void setTtl(final int ttl) {
    this.ttl = ttl;
  }

  /**
   * Set to `true` to tell Cloudflare to activate proxying
   */
  private boolean proxied;
  public boolean isProxied() {
    return proxied;
  }

  public void setProxied(final boolean proxied) {
    this.proxied = proxied;
  }

  @Override
  public boolean updateDns(final InetAddress ip) {
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

      final HttpRequest req = HttpRequest.newBuilder(requestUri)
        .PUT(HttpRequest.BodyPublishers.ofString(payload))
        .header("Authorization", "Bearer " + this.getApiToken())
        .header("Content-Type", "application/json")    
        .build();

      final HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() == 200) {
        getLogger().info(String.format("Successfully updated %s to %s", this.getDomain(), ip.getHostAddress()));
        return true;
      }

      final String message = resp.body();
      getLogger().severe(String.format("Cloudflare API returned HTTP %d: %s", resp.statusCode(), message));
    } catch (IOException _) {
      getLogger().severe("Update to Cloudflare failed with IOException");
    } catch (InterruptedException _) {
      getLogger().severe("Update to Cloudflare failed with InterruptedException");
    } catch (URISyntaxException _) {
      getLogger().severe(String.format("Failed to format zone_id and record_id into a proper URI: %s", rawUri));
    } catch (Exception e) {
      getLogger().severe("Update to Cloudflare failed " + e.getMessage());
    }
    return false;
  }
}
