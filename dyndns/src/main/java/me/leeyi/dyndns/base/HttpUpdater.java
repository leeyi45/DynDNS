package me.leeyi.dyndns.base;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Updater that leverages HTTP to do its updates
 */
public abstract class HttpUpdater extends DNSUpdater {  
  public HttpUpdater(
    final @NotNull Logger logger,
    final @NotNull JavaPlugin parent,
    final URI ipApiAddress
  ) {
    super(logger, parent);
    this.ipApiAddress = ipApiAddress;
  }

  protected final HttpClient httpClient = HttpClient.newHttpClient();

  private URI ipApiAddress;

  public void setIpApiAddress(final URI ipApiAddress) {
    this.ipApiAddress = ipApiAddress;
  }

  public URI getIpApiAddress() {
    return ipApiAddress;
  }

  /**
   * Gets the current public IP address from the specified API.
   * @return String representing the public IP, or `null` if the API request failed.
   */
  @Override 
  protected @Nullable InetAddress getCurrentIp() {
    final HttpRequest req = HttpRequest.newBuilder()
      .uri(this.ipApiAddress)
      .GET()
      .build();

    try {
      final HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

      if (resp.statusCode() == 200) {
        final String ip = resp.body().strip();

        try {
          // Validate that we did get an IP address
          return InetAddress.ofLiteral(ip);
        } catch (IllegalArgumentException _) {
          getLogger().severe(String.format("Reply from %s did not return a valid IP address: %s", this.ipApiAddress, ip));
          return null;
        }
      }
      getLogger().severe(String.format("%s returned HTTP %d", this.ipApiAddress, resp.statusCode()));
    } catch (IOException _) {
      getLogger().severe(String.format("Request to %s failed with IOException", this.ipApiAddress));
    } catch (InterruptedException _) {
      getLogger().severe(String.format("Request to %s failed with InterruptedException", this.ipApiAddress));
    } 

    return null;
  }
}
