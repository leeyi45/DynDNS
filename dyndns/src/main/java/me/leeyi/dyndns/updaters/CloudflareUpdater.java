package me.leeyi.dyndns.updaters;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.leeyi.dyndns.base.HttpUpdater;

/**
 * Concrete implementation for Cloudflare DNS
 */
public class CloudflareUpdater extends HttpUpdater {
  public CloudflareUpdater(
    final @NotNull Logger logger,
    final @NotNull JavaPlugin parent,
    final URI ipApiAddress,
    final @NotNull String domain,
    final @NotNull String zoneId,
    final @NotNull String recordId,
    final @NotNull String apiToken,
    final int ttl
  ) {
    super(logger, parent, ipApiAddress);
    this.domain = domain;
    this.zoneId = zoneId;
    this.recordId = recordId;
    this.apiToken = apiToken;
    this.ttl = ttl;
  }

  @Override
  public @NotNull String getName() { return "cloudflare"; }

  /**
   * Domain to be updated, i.e `domain.toupdate.com`
   */
  private @NotNull String domain;

  public void setDomain(final @NotNull String domain) {
    this.domain = domain;
  }

  public @NotNull String getDomain() {
    return domain;
  }

  private @NotNull String zoneId;
  public void setZoneId(final @NotNull String zoneId) {
    this.zoneId = zoneId;
  }

  public @NotNull String getZoneId() {
    return zoneId;
  }

  private @NotNull String recordId;
  public void setRecordId(final @NotNull String recordId) {
    this.recordId = recordId;
  }

  public @NotNull String getRecordId() {
    return recordId;
  }

  private @NotNull String apiToken;
  public void setApiToken(final @NotNull String apiToken) {
    this.apiToken = apiToken;
  }

  public @NotNull String getApiToken() {
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
  protected boolean updateDnsInternal(final InetAddress ip) {
    final String rawUri = String.format("https://api.cloudflare.com/client/v4/zones/%s/dns_records/%s", this.getZoneId(), this.getRecordId());
    final String payload = String.format("{\"type\":\"A\",\"name\":\"%s\",\"content\":\"%s\",\"ttl\":%d,\"proxied\":%s}",
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
        getLogger().info(String.format("Successfully updated %s to %s", this.getDomain(), ip));
        return true;
      }

      getLogger().severe(String.format("Cloudflare API returned HTTP %d", resp.statusCode()));
    } catch (IOException e) {
      getLogger().severe("Update to Cloudflare failed with IOException");
    } catch (InterruptedException e) {
      getLogger().severe("Update to Cloudflare failed with InterruptedException");
    } catch (URISyntaxException e) {
      getLogger().severe(String.format("Failed to format zone_id and record_id into a proper URI: %s", rawUri));
    } catch (Exception e) {
      getLogger().severe("Update to Cloudflare failed " + e.getMessage());
    }
    return false;
  }

  @Override 
  public ConfigurationSection toConfigurationSection() {
    final var config = super.toConfigurationSection();

    config.set("domain", this.getDomain());
    config.set("record_id", this.getRecordId());
    config.set("zone_id", this.getZoneId());
    config.set("ttl", this.getTtl());
    config.set("proxied", this.isProxied());
    config.set("api_token", this.getApiToken());

    return config;
  }

  @Override
  public List<String> fromConfigurationSection(final ConfigurationSection config) {
    final List<String> errors = super.fromConfigurationSection(config);

    final Function<String, @Nullable String> stringHandler = option -> {
      final String value = config.getString(option);
      if (value == null) {
        errors.add(String.format("No %s defined for %s!", option, this.getName()));
        return null;
      }
      
      return value;
    };

    this.setDomain(stringHandler.apply("domain"));
    this.setRecordId(stringHandler.apply("record_id"));
    this.setZoneId(stringHandler.apply("zone_id"));
    this.setApiToken(stringHandler.apply("api_token"));
    this.setProxied(config.getBoolean("proxied"));
    this.setTtl(config.getInt("ttl"));

    return errors;
  }
}
