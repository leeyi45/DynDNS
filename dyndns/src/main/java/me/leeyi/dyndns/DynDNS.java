package me.leeyi.dyndns;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
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
      if (updaters.containsKey(protocol)) {
        // If it was already running, stop the running task
        updaters.get(protocol).cancelTask();
      }

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

  private LiteralArgumentBuilder<CommandSourceStack> createCommand(
    final String name,
    final BiFunction<DNSUpdater, CommandContext<CommandSourceStack>, Integer> handler
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
          return handler.apply(updater, ctx);
        }
        return 1;
      });
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
          createCommand("stop", (updater, ctx) -> {
            return updater.stopCommandHandler(ctx);
          })
        )
        .then(
          createCommand("start", (updater, ctx) -> {
            return updater.startCommandHandler(ctx);
          })
        )
        .then(
          createCommand("lastAddress", (updater, ctx) -> {
            return updater.lastCommandHandler(ctx);
          })
        )
        .then(
          createCommand("status", (updater, ctx) -> {
            return updater.statusCommandHandler(ctx);
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
  }

  @Override
  public void onDisable() {
    for (final DNSUpdater updater : updaters.values()){
      updater.cancelTask();
    }

    this.logger.info("DynDNS Plugin disabled");
  }
}
