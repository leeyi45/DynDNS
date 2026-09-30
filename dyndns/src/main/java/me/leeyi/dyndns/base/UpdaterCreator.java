package me.leeyi.dyndns.base;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import me.leeyi.dyndns.InvalidConfigException;

@FunctionalInterface
public interface UpdaterCreator {
  public DNSUpdater create(
    final @NotNull JavaPlugin parent,
    final @NotNull ConfigurationSection config
  ) throws InvalidConfigException;
}
