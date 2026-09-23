package me.leeyi.dyndns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.leeyi.dyndns.base.DNSUpdater;
import me.leeyi.dyndns.updaters.CloudflareUpdater;
import me.leeyi.dyndns.updaters.UpdaterCreator;

public class DynDNS extends JavaPlugin {
  private final Map<String, UpdaterCreator> updaterCreators = Map.of(
    "cloudflare", CloudflareUpdater::new
  );

  private final Map<String, DNSUpdater> updaters = new HashMap<String, DNSUpdater>();
  private Logger logger;

  private int reloadCommandHandler(final CommandContext<CommandSourceStack> ctx) {
    final String protocol = ctx.getArgument("protocol", String.class);
    final CommandSender sender = ctx.getSource().getSender();

    if (!updaterCreators.containsKey(protocol)) {
      sender.sendPlainMessage(String.format("No such protocol %s", protocol));
    }

    final ConfigurationSection configSection = getConfig().getConfigurationSection(protocol);

    if (configSection == null) {
      sender.sendPlainMessage(String.format("No config for %s, skipping...", protocol));
    } else {
      try {
        final UpdaterCreator creator = updaterCreators.get(protocol);
        updaters.put(protocol, creator.create(getLogger(), this, configSection));
      } catch (InvalidConfigException e) {

      }
    }

    return 1;
  }

  private final void initializeUpdaters() {
    final FileConfiguration config = getConfig();
    
    for (Map.Entry<String, UpdaterCreator> kv : updaterCreators.entrySet()) {
      final String protocol = kv.getKey();
      final ConfigurationSection configSection = config.getConfigurationSection(protocol);

      if (configSection == null) {
        this.logger.warning(String.format("No config for %s, skipping...", protocol));
      } else {
        try {
          updaters.put(kv.getKey(), kv.getValue().create(getLogger(), this, configSection));
        } catch (InvalidConfigException e) {
          this.logger.severe(String.format("Invalid config for %s: %s", protocol, e.getMessage()));
        }
      }
    }
  }

  private LiteralArgumentBuilder<CommandSourceStack> createProtocolCommand(
    final String name,
    final BiFunction<DNSUpdater, CommandSender, Integer> handler
  ) {
    return Commands.literal(name)
      .then(Commands.argument("protocol", StringArgumentType.word()))
      .executes(ctx -> {
        final String protocol = ctx.getArgument("protocol", String.class);
        final CommandSender sender = ctx.getSource().getSender();

        if (!updaters.containsKey(protocol)) {
          sender.sendPlainMessage(String.format("Unknown protocol \"%s\".", protocol));
        } else {
          final DNSUpdater updater = updaters.get(protocol);
          return handler.apply(updater, sender);
        }
        return 1;
      });
  }

  /**
   * Address of the IP API to request the IP address from.
   */
  private URI ipApiAddress;

  /**
   * Last IP Address that was retrieved from the IP API.
   */
  private @Nullable InetAddress lastRetrievedIp = null;

  /**
   * How long to wait in between updating the DNS in seconds.
   */
  private int updateInterval;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
  private @Nullable ScheduledFuture<?> updateTask;

  private final void updateDns() {
    try {
      final HttpRequest req = HttpRequest.newBuilder(this.ipApiAddress)
        .GET()
        .build();

      final HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString());

      if (resp.statusCode() != 200) {
        logger.severe(String.format("Failed to retrieve IP from %s: HTTP %d", this.ipApiAddress, resp.statusCode()));
        return;
      }

      try {
        final InetAddress newIp = InetAddress.ofLiteral(resp.body().strip());
        
        if (lastRetrievedIp != null && lastRetrievedIp.equals(newIp)) {
          logger.info(String.format("New IP %s is the same as last received IP, not proceeding with updates.", newIp.getHostAddress()));
          return;
        }

        for (DNSUpdater updater : updaters.values()) {
          if (updater.isEnabled()) {
            executorService.schedule(() -> { updater.updateDns(newIp); }, 0L, TimeUnit.SECONDS);
          }
        }
      } catch (IllegalArgumentException _) {
        logger.severe(String.format("%s did not return a valid IP address", this.ipApiAddress));
        return;
      }
    } catch (IOException e) {
      logger.severe(String.format("HTTP request to %s failed with IOException: %s", this.ipApiAddress, e.getMessage()));
    } catch (InterruptedException e) {
      logger.severe(String.format("HTTP request to %s was cancelled.", this.ipApiAddress));
    }
  }

  @Override
  public void onEnable() {
    this.logger = getLogger();
    this.logger.info("DynDNS Plugin enabled");
    this.saveResource("config.yml", false);

    initializeUpdaters();

    this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
      final var command = Commands.literal("dyndns")
        .then(
          createProtocolCommand("stop", (updater, sender) -> {
            if (!updater.isEnabled()) {
              sender.sendPlainMessage(String.format("%s was already stopped.", updater.getName()));
            } else {
              updater.setEnabled(false);
              sender.sendPlainMessage(String.format("%s stopped.", updater.getName()));
            }
            return 1;
          })
        )
        .then(
          createProtocolCommand("start", (updater, sender) -> {
            if (updater.isEnabled()) {
              sender.sendPlainMessage(String.format("%s was already running.", updater.getName()));
            } else {
              updater.setEnabled(true);
              sender.sendPlainMessage(String.format("%s started.", updater.getName()));
            }
            return 1;
          })
        )
        .then(
          Commands.literal("lastAddress")
            .executes(ctx -> {
              final CommandSender sender = ctx.getSource().getSender();
             
              if (lastRetrievedIp != null) {
                sender.sendPlainMessage(String.format("Last IP retrieved from %s is %s", this.ipApiAddress, this.lastRetrievedIp));
              } else {
                sender.sendPlainMessage("No last retrieved IP");
              }
              return 1;
            })
        )
        .then(
          createProtocolCommand("status", (updater, sender) -> {
            sender.sendPlainMessage(String.format(
              "%s is %s.",
              updater.getName(),
              updater.isEnabled() ? "running" : "stopped"
            ));
            return 1;
          })
        )
        .then(
          Commands.literal("reload")
            .then(Commands.argument("protocol", StringArgumentType.word()))
            .executes(this::reloadCommandHandler)
        )
        .build();

      commands.registrar().register(command);
    });

    final FileConfiguration config = getConfig();

    final int rawUpdateInterval = config.getInt("interval", 100);
    if (rawUpdateInterval < -1) {
      logger.severe(String.format("Invalid value for interval %d: Must be greater than -1", rawUpdateInterval));
      return;
    } else {
      this.updateInterval = rawUpdateInterval;
    }

    final String rawIpApi = config.getString("api_address");
    if (rawIpApi == null) {
      logger.severe("No IP api address provided!");
    }

    try {
      this.ipApiAddress = new URI(rawIpApi);
    } catch (URISyntaxException _) {
      logger.severe(String.format("Invalid value for api_address: %s", rawIpApi));
    }

    if (this.ipApiAddress != null) {
      if (updateInterval == -1) {
        updateTask = executorService.scheduleAtFixedRate(this::updateDns, 0L, updateInterval, TimeUnit.SECONDS);
      } else {
        executorService.submit(this::updateDns);
      }
    }
  }

  @Override
  public void onDisable() {
    if (updateTask != null && !updateTask.isCancelled()) {
      updateTask.cancel(false);
    }
    this.logger.info("DynDNS Plugin disabled");
  }
}
