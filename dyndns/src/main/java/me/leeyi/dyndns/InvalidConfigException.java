package me.leeyi.dyndns;

import me.leeyi.dyndns.base.DNSUpdater;

public class InvalidConfigException extends Exception {
  public InvalidConfigException(final DNSUpdater parent, final String message) {
    super(message);
  }
}
