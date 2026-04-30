# public-transport-web-api

Small Spring Boot web API around [public-transport-enabler](https://github.com/schildbach/public-transport-enabler).

The project tracks PTE from its `master` branch because upstream does not publish releases or tags. A weekly GitHub Actions workflow checks for new upstream commits and opens an update PR.

## Requirements

- Java 25 LTS
- Docker, optional for container builds
- Git submodules enabled for PTE

## Checkout

```bash
git clone --recurse-submodules https://github.com/fewi/public-transport-web-api.git
cd public-transport-web-api
```

If the repository was already cloned without submodules:

```bash
git submodule update --init --recursive
```

## Run Locally

```bash
./gradlew bootRun
```

The API listens on `http://localhost:8080` by default.

## Build And Test

```bash
./gradlew test bootJar
```

## Configuration

Spring Boot environment variables can override the defaults:

- `SERVER_PORT`, default `8080`
- `PROVIDER_DEFAULT`, default `Kvv`
- `PROVIDERKEY_BVG`, optional BVG authorization value
- `PROVIDERKEY_VBB`, optional VBB authorization value
- `THINGSPEAK_KEY`, optional ThingSpeak key
- `THINGSPEAK_CHANNEL`, default `field1`

`VagfrProvider` and `VmsProvider` no longer exist in current PTE, so `Kvv` is now the default provider.

## Docker

Build the image:

```bash
docker build -t public-transport-web-api .
```

Run the container:

```bash
docker run --rm -p 8080:8080 public-transport-web-api
```

Pass provider keys as environment variables when needed:

```bash
docker run --rm -p 8080:8080 -e PROVIDERKEY_BVG=... public-transport-web-api
```

## PTE Updates

`.github/workflows/update-pte.yml` runs weekly and can also be started manually.

When a new PTE commit exists, the workflow updates the submodule and runs `./gradlew test bootJar`.

If verification passes, it opens or updates a normal PR. If verification fails, it opens or updates a draft PR and fails the workflow so the broken upstream integration is visible.

## Endpoints

### `GET /provider`

Lists available PTE providers discovered on the classpath.

### `GET /station/suggest`

Suggests matching station locations.

Parameters:

- `q`, station query
- `provider`, optional provider name, example `Kvv`
- `locationType`, optional `ANY`, `STATION`, `ADDRESS`, `POI`, `COORD`, or `*`

Example:

```text
/station/suggest?q=Hauptbahnhof&provider=Kvv
```

### `GET /departure`

Lists departures for a station.

Parameters:

- `from`, station id
- `provider`, optional provider name, example `Kvv`
- `limit`, optional result limit, default `10`

Example:

```text
/departure?from=de:08212:1000&provider=Kvv&limit=10
```

### `GET /connection`

Lists direct trips between two stations.

Parameters:

- `from`, origin station id
- `to`, destination station id
- `product`, product code such as `T` for tram or `B` for bus
- `timeOffset`, optional minutes added to the departure time
- `provider`, optional provider name, example `Kvv`

Example:

```text
/connection?from=START_ID&to=DESTINATION_ID&product=T&provider=Kvv
```

### `GET /connectionEsp`

Returns a compact JSON response for microcontroller clients.

### `GET /departureFHEM`

Returns compact departure data for FHEM integrations.

### `GET /connectionRaw`

Returns the raw PTE trip response.

## v2 Endpoints

- `GET /v2/provider`
- `GET /v2/station/nearby`
- `GET /v2/station/suggest`
- `GET /v2/departure`
