package me.leeyi.dyndns.updaters;

import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.leeyi.dyndns.InvalidConfigException;
import me.leeyi.dyndns.base.DNSUpdater;

@FunctionalInterface
public interface UpdaterCreator {
  public DNSUpdater create(
    final @NotNull Logger logger,
    final @NotNull JavaPlugin parent,
    final @Nullable ConfigurationSection config
  ) throws InvalidConfigException;
}
