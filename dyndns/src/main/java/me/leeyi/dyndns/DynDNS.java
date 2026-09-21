package me.leeyi.dyndns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

public class DynDNS extends JavaPlugin {
  /**
   * Domain to be updated, i.e `domain.toupdate.com`
   */
  private String domain;
  private String zoneId;
  private String recordId;
  private String apiToken;
  private int ttl;

  /**
   * Set to `true` to tell Cloudflare to activate proxying
   */
  private boolean proxied;

  /**
   * API to contact to obtain IP address
   */
  private URI apiAddress;
  private @Nullable String lastRetrievedIp;
  
  /**
   * Number of seconds to wait in between IP requests.
   * Set to -1 to only request on plugin enable
   */
  private int interval;

  private Logger logger;
  private BukkitTask updateTask;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  private final List<String> loadConfig() {
    final FileConfiguration config = getConfig();
    final List<String> errors = new ArrayList<>();

    final String configDomain = config.getString("domain");
    if (configDomain == null) {
      errors.add("No domain configured!");
    }

    final String configZoneId = config.getString("zone_id");
    if (configZoneId == null) {
      errors.add("No zone id configured!");
    }

    final String configRecordId = config.getString("record_id");
    if (configRecordId == null) {
      errors.add("No record id configured!");
    }

    final String configApiToken = config.getString("api_token");
    if (configApiToken == null) {
      errors.add("No api token configured!");
    }

    final String configApiAddress = config.getString("api_address", "https://api.ipify.org");

    if (errors.size() == 0) {
      try {
        this.apiAddress = new URI(configApiAddress);
      } catch (URISyntaxException _) {
        errors.add(String.format("Invalid URI for api_address: %s", configApiAddress));
        return errors;
      }

      this.ttl = config.getInt("ttl", 1);
      this.proxied = config.getBoolean("proxied", false);
      this.interval = config.getInt("interval", 3000);
      
      this.domain = configDomain;
      this.zoneId = configZoneId;
      this.recordId = configRecordId;
      this.apiToken = configApiToken;
    }

    return errors;
  }

  /**
   * Gets the current public IP address from the specified API.
   * @return String representing the public IP, or `null` if the API request failed.
   */
  private final @Nullable String getCurrentIp() {
    final HttpRequest req = HttpRequest.newBuilder()
      .uri(this.apiAddress)
      .GET()
      .build();

    try {
      final HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

      if (resp.statusCode() == 200) {
        final String ip = resp.body().strip();

        try {
          // Validate that we did get an IP address
          InetAddress.ofLiteral(ip);
        } catch (IllegalArgumentException _) {
          logger.severe(String.format("Reply from %s did not return a valid IP address: %s", this.apiAddress, ip));
          return null;
        }

        return ip;
      }
      logger.severe(String.format("%s returned HTTP %d", this.apiAddress, resp.statusCode()));
    } catch (IOException _) {
      logger.severe(String.format("Request to %s failed with IOException", this.apiAddress));
    } catch (InterruptedException _) {
      logger.severe(String.format("Request to %s failed with InterruptedException", this.apiAddress));
    } 

    return null;
  }

  private final void updateDnsEntry() {
    final String ip = getCurrentIp();
    if (ip == null) return;
    if (ip == this.lastRetrievedIp) {
      this.logger.info(String.format("Last retrieved IP was also %s, not updating...", ip));
      return;
    }

    this.lastRetrievedIp = ip;

    final String rawUri = String.format("https://api.cloudflare.com/client/v4/zones/%s/dns_records/%s", this.zoneId, this.recordId);
    final String payload = String.format("{\"type\":\"A\",\"name\":\"%s\",\"content\":\"%s\",\"ttl\":%d,\"proxied\":%s}",
      this.domain,
      ip,
      this.ttl,
      this.proxied ? "true" : "false"
    );

    try {
      final URI requestUri = new URI(rawUri);

      final HttpRequest req = HttpRequest.newBuilder(requestUri)
        .PUT(HttpRequest.BodyPublishers.ofString(payload))
        .header("Authorization", "Bearer " + this.apiToken)
        .header("Content-Type", "application/json")    
        .build();

      final HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() == 200) {
        logger.info(String.format("Successfully updated %s to %s", this.domain, ip));
        return;
      }

      logger.severe(String.format("Cloudflare API returned HTTP %d", resp.statusCode()));
    } catch (IOException e) {
      logger.severe("Update to Cloudflare failed with IOException");
    } catch (InterruptedException e) {
      logger.severe("Update to Cloudflare failed with InterruptedException");
    } catch (URISyntaxException e) {
      logger.severe(String.format("Failed to format zone_id and record_id into a proper URI: %s", rawUri));
    } catch (Exception e) {
      logger.severe("Update to Cloudflare failed " + e.getMessage());
    }
  }

  private final boolean cancelUpdateTask() {
    if (this.updateTask != null && !this.updateTask.isCancelled()) {
      this.updateTask.cancel();
      return true;
    } else {
      return false;
    }
  }

  private final void startUpdateTask(final BukkitRunnable runnable) {
    if (interval != -1) {
      this.updateTask = runnable.runTaskTimerAsynchronously(this, 0L, interval * 20);
    } else {
      runnable.runTask(this);
    }
  }

  private final int stopCommandHandler(final CommandContext<CommandSourceStack> ctx) {
    final CommandSender sender = ctx.getSource().getSender();

    if (cancelUpdateTask()) {
      sender.sendPlainMessage("Automatic DNS updates stopped.");
    } else {
      sender.sendPlainMessage("No Automatic DNS task running.");
    }
    return 1;
  }

  private final Command<CommandSourceStack> getStartCommandHandler(final BukkitRunnable runnable) {
    return ctx -> {
      final CommandSender sender = ctx.getSource().getSender();

      startUpdateTask(runnable);
      if (interval != -1) {
        sender.sendPlainMessage(String.format("Automatic DNS task started with interval %d", this.interval));
      } else {
        sender.sendPlainMessage("Update task ran once.");
      }
      return 1;
    };
  }

  private final int lastCommandHandler(final CommandContext<CommandSourceStack> ctx) {
    final CommandSender sender = ctx.getSource().getSender();

    if (this.lastRetrievedIp == null) {
      sender.sendPlainMessage(String.format("Have not yet retrieved IP address from %s", this.apiAddress));
    } else {
      sender.sendPlainMessage(String.format("Last retrieved IP address was %s", this.lastRetrievedIp));
    }

    return 1;
  }

  private final Command<CommandSourceStack> getReloadCommandHandler(final BukkitRunnable runnable) {
    return ctx -> {
      final List<String> configLoadErrors = loadConfig();
      final CommandSender sender = ctx.getSource().getSender();
      
      if (configLoadErrors.size() > 0) {
        sender.sendPlainMessage("Config failed to load");
        configLoadErrors.forEach(sender::sendPlainMessage);
      } else {
        startUpdateTask(runnable);
        sender.sendPlainMessage("Config reloaded.");
      }
      return 1;
    };
  }

  private final int statusCommandHandler(final CommandContext<CommandSourceStack> ctx) {
    final CommandSender sender = ctx.getSource().getSender();

    if (updateTask != null && !updateTask.isCancelled()) {
      sender.sendPlainMessage("Automatic DNS task is running");
    } else {
      sender.sendPlainMessage("No automatic DNS task running.");
    }

    return 1;
  }

  @Override
  public void onEnable() {
    this.logger = getLogger();
    this.logger.info("DynDNS Plugin enabled");
    this.saveResource("config.yml", false);

    final List<String> configLoadErrors = loadConfig();

    final BukkitRunnable updateRunnable = new BukkitRunnable() {
      @Override
      public void run() { updateDnsEntry(); }
    };

    this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
      final var command = Commands.literal("dyndns")
        .then(
          Commands.literal("stop")
          .executes(this::stopCommandHandler)
        )
        .then(
          Commands.literal("start")
            .executes(this.getStartCommandHandler(updateRunnable))
        )
        .then(
        Commands.literal("lastAddress")
          .executes(this::lastCommandHandler)
        )
        .then(
          Commands.literal("reload")
            .executes(this.getReloadCommandHandler(updateRunnable))
        )
        .then(
          Commands.literal("status")
            .executes(this::statusCommandHandler)
        ).build();

      commands.registrar().register(command);
    });

    if (configLoadErrors.size() > 0) {
      configLoadErrors.forEach(this.logger::severe);
      return;
    }

    startUpdateTask(updateRunnable);
  }

  @Override
  public void onDisable() {
    cancelUpdateTask();

    httpClient.close();

    this.logger.info("DynDNS Plugin disabled");
  }
}
