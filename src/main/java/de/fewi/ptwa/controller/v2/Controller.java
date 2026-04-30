package de.fewi.ptwa.controller.v2;

import de.fewi.ptwa.controller.v2.model.DepartureData;
import de.fewi.ptwa.controller.v2.model.Provider;
import de.fewi.ptwa.controller.v2.model.ProviderEnum;
import de.fewi.ptwa.util.ProviderUtil;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;
import de.schildbach.pte.dto.SuggestLocationsResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@RestController
public class Controller {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmZ")
            .withZone(ZoneId.systemDefault());

    @GetMapping("/v2/provider")
    public ResponseEntity<List<Provider>> providerlist() throws IOException {
        List<Provider> list = new ArrayList<>();
        for (ProviderEnum each : ProviderEnum.values()) {
            list.add(each.asProvider());
        }
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(list);
    }

    @GetMapping("/v2/station/nearby")
    public ResponseEntity<?> findNearbyLocations(@RequestParam(value = "provider", required = false) String providerName) {
        NetworkProvider networkProvider = getNetworkProvider(providerName);
        if (networkProvider == null) {
            return providerNotFound(providerName);
        }
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(networkProvider.defaultProducts());
    }

    @GetMapping("/v2/station/suggest")
    public ResponseEntity<?> suggest(@RequestParam("q") final String query, @RequestParam(value = "provider", required = false) String providerName, @RequestParam(value = "locationType", required = false) String stationType) throws IOException {
        NetworkProvider provider = getNetworkProvider(providerName);
        if (provider == null) {
            return providerNotFound(providerName);
        }

        SuggestLocationsResult suggestLocations = provider.suggestLocations(query);
        if (SuggestLocationsResult.Status.OK.equals(suggestLocations.status)) {
            LocationType locationType = getLocationType(stationType);
            List<Location> resultList = new ArrayList<>();
            if (locationType == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("LocationType " + stationType + " not found or can not instantiated...");
            } else if (!LocationType.ANY.equals(locationType)) {
                for (Location loc : suggestLocations.getLocations()) {
                    if (locationType.equals(loc.type)) {
                        resultList.add(loc);
                    }
                }
            } else {
                resultList.addAll(suggestLocations.getLocations());
            }
            return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(resultList);
        }
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Remote Service is down or temporarily not available");
    }

    @GetMapping("/v2/departure")
    public ResponseEntity<?> departure(@RequestParam("from") String from, @RequestParam(value = "provider", required = false) String providerName, @RequestParam(value = "limit", defaultValue = "10") int limit, @RequestParam(value = "numberFilter", required = false) String numberFilter, @RequestParam(value = "toFilter", required = false) String toFilter) throws IOException {
        NetworkProvider provider = getNetworkProvider(providerName);
        if (provider == null) {
            return providerNotFound(providerName);
        }
        QueryDeparturesResult efaData = provider.queryDepartures(from, new Date(), 120, true);
        if (efaData.status.name().equals("OK")) {
            List<DepartureData> list = new ArrayList<>();
            if (efaData.findStationDepartures(from) == null && !efaData.stationDepartures.isEmpty()) {
                for (StationDepartures stationDeparture : efaData.stationDepartures) {
                    list.addAll(convertDepartures(stationDeparture, numberFilter, toFilter));
                }
                Collections.sort(list);
            } else {
                list.addAll(convertDepartures(efaData.findStationDepartures(from),numberFilter, toFilter));
            }
            if (list.size() > limit) {
                list = list.subList(0, limit);
            }
            return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(list);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EFA error status: " + efaData.status.name());
    }

    private List<DepartureData> convertDepartures(StationDepartures stationDepartures, String numberFilter, String toFilter) {
        Calendar cal = Calendar.getInstance();
        List<DepartureData> list = new ArrayList<>();
        if (stationDepartures == null) {
            return list;
        }

        for (Departure departure : stationDepartures.departures) {
            if (!isIncluded(departure, numberFilter, toFilter)) {
                continue;
            }
            DepartureData data = new DepartureData();
            data.setMessage(departure.message);
            if (departure.destination != null) {
                data.setTo(departure.destination.name);
                data.setToId(departure.destination.id);
            }
            data.setProduct(departure.line.product.toString());
            data.setNumber(departure.line.label);
            if (departure.position != null) {
                data.setPlatform(departure.position.name);
            }

            Date departureTime = departure.getTime();
            data.setDepartureTime(DATE_FORMAT.format(departureTime.toInstant()));
            data.setDepartureTimestamp(departureTime.getTime());
            if (departure.predictedTime != null && departure.plannedTime != null && departure.predictedTime.after(departure.plannedTime)) {
                data.setDepartureDelay((departure.predictedTime.getTime() - departure.plannedTime.getTime()) / 1000 / 60);
            }

            long time = departureTime.getTime() - cal.getTimeInMillis();
            float depMinutes = (float) time / 1000 / 60;
            data.setDepartureTimeInMinutes((int) Math.ceil(depMinutes));
            list.add(data);
        }
        return list;
    }

    private LocationType getLocationType(String locationType) {
        if (locationType == null || "*".equals(locationType)) {
            return LocationType.ANY;
        }
        try {
            return LocationType.valueOf(locationType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isIncluded(Departure stationDeparture, String numberFilter, String toFilter) {
        if (toFilter != null && !toFilter.equals("*")) {
            Location dest = stationDeparture.destination;
            if (!(dest != null && dest.name != null && toFilter.equalsIgnoreCase(dest.name))) {
                return false;
            }
        }
        if (numberFilter != null && !numberFilter.equals("*")) {
            Line line = stationDeparture.line;
            if (!(line != null && line.label != null && numberFilter.equalsIgnoreCase(line.label))) {
                return false;
            }
        }
        return true;
    }

    private NetworkProvider getNetworkProvider(String providerName) {
        return ProviderUtil.getObjectForProvider(providerName);
    }

    private ResponseEntity<String> providerNotFound(String providerName) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Provider " + providerName + " not found or can not instantiated...");
    }
}
