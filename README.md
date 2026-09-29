# DynDNS

A dynamic DNS plugin for [PaperMC](https://papermc.io) servers.

The plugin will poll a given IP API at regular intervals to determine if the server's public IP address has changed. Only if the public has changed will the plugin
send an update to all the configured DNS providers.

Currently supported DNS providers:
1. Cloudflare

## Configuration

The global `api_address` setting should be set to a URI that responds to HTTP GET requests with only the IP address of the requesting client. An example is https://api.ipify.org, which is the default
IP API used when no `api_address` is specified.

If the IP API doesn't return a valid IPv4 or IPv6 address, the plugin will throw an error and refuse to send out the DNS updates.

> [!WARNING]
> You should always use a https connection for this so that someone isn't able to MITM your IP request and thereby allow them to hijack your domain
> and have it redirect to their IP address instead.

The `interval` setting controls how often the API will be polled for the IP address. Set to `-1` to only execute on server startup.

### Sample Configuration

```yml
# config.yml
api_address: https://api.ipify.org
interval: 1000 # 1000 seconds

cloudflare:
  enabled: true # True if omitted. Set to false to disable.

  # Refer to the Cloudflare API for exactly what you should populate
  # these fields with
  domain: your.domain.com
  zone_id:
  record_id:
  api_token:
  ttl: 300
  proxied: true
```