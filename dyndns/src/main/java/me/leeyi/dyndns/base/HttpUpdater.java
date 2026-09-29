package me.leeyi.dyndns.base;

import java.io.IOException;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.leeyi.dyndns.InvalidConfigException;

public abstract class HttpUpdater extends DNSUpdater {
  public HttpUpdater(
    final @NotNull Logger logger,
    final @NotNull JavaPlugin parent,
    final @NotNull ConfigurationSection config
  ) throws InvalidConfigException {
    super(logger, parent, config);
  }

  protected final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  public boolean updateDns(final InetAddress ip) {
    try {
      final @Nullable HttpResponse<String> resp = httpClient.send(getHttpRequest(ip), BodyHandlers.ofString());
      if (resp == null) return false;

      final String message = resp.body();

      getLogger().severe(String.format("%s API returned HTTP %d: %s", this.getName(), resp.statusCode(), message));
    } catch (IOException _) {
      getLogger().severe(String.format("Update to %s failed with IOException", this.getName()));
    } catch (InterruptedException _) {
      getLogger().severe(String.format("Update to %s failed with InterruptedException", this.getName()));
    } catch (Exception e) {
      getLogger().severe(String.format("Update to %s failed: %s", this.getName(), e.getMessage()));
    }
    return false;
  }

  /**
   * Internal method used to create the HTTP request that will get sent out to update the DNS entry.
   * Returns `null` when the HTTP request failed to be created for whatever reason.
   */
  protected abstract @Nullable HttpRequest getHttpRequest(final InetAddress ip);
}
