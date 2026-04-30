package de.fewi.ptwa.controller;

import de.fewi.ptwa.entity.TripData;
import de.fewi.ptwa.util.ProviderUtil;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryTripsContext;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.Trip;
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
import java.util.Date;
import java.util.List;

@RestController
public class ConnectionController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmZ")
            .withZone(ZoneId.systemDefault());

    @GetMapping("/connection")
    public ResponseEntity<?> connection(@RequestParam("from") String from, @RequestParam("to") String to, @RequestParam(value = "provider", required = false) String providerName, @RequestParam("product") char product, @RequestParam(value = "timeOffset", defaultValue = "0") int timeOffset) throws IOException {
        NetworkProvider provider = ProviderUtil.getObjectForProvider(providerName);
        if (provider == null) {
            return providerNotFound(providerName);
        }

        Date plannedDepartureTime = plannedDepartureTime(timeOffset);
        QueryTripsResult efaData = queryTrips(provider, from, to, product, plannedDepartureTime);
        if (efaData.status.name().equals("OK")) {
            List<TripData> list = filterTrips(efaData.trips, from, to, "normal", plannedDepartureTime);

            if (list.isEmpty()) {
                List<TripData> retryList = findMoreTrips(efaData.context, from, to, "normal", provider, plannedDepartureTime);
                if (retryList.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No trip found.");
                }
                return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(retryList);
            }
            return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(list);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EFA error status: " + efaData.status.name());
    }

    @GetMapping("/connectionEsp")
    public ResponseEntity<?> departureEsp(@RequestParam("from") String from, @RequestParam("to") String to, @RequestParam(value = "provider", required = false) String providerName, @RequestParam("product") char product, @RequestParam(value = "timeOffset", defaultValue = "0") int timeOffset) throws IOException {
        NetworkProvider provider = ProviderUtil.getObjectForProvider(providerName);
        if (provider == null) {
            return providerNotFound(providerName);
        }

        Date plannedDepartureTime = plannedDepartureTime(timeOffset);
        QueryTripsResult efaData = queryTrips(provider, from, to, product, plannedDepartureTime);
        if (efaData.status.name().equals("OK")) {
            List<TripData> list = filterTrips(efaData.trips, from, to, "esp", plannedDepartureTime);

            if (list.isEmpty()) {
                List<TripData> retryList = findMoreTrips(efaData.context, from, to, "esp", provider, plannedDepartureTime);
                if (retryList.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No trip found.");
                }
                return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(toEspResponse(retryList.get(0)));
            }
            return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(toEspResponse(list.get(0)));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EFA error status: " + efaData.status.name());
    }

    @GetMapping("/connectionRaw")
    public ResponseEntity<?> test(@RequestParam("from") String from, @RequestParam("to") String to, @RequestParam(value = "provider", required = false) String providerName, @RequestParam("product") char product, @RequestParam(value = "timeOffset", defaultValue = "0") int timeOffset) throws IOException {
        NetworkProvider provider = ProviderUtil.getObjectForProvider(providerName);
        if (provider == null) {
            return providerNotFound(providerName);
        }

        QueryTripsResult efaData = queryTrips(provider, from, to, product, plannedDepartureTime(timeOffset));
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(efaData.trips);
    }

    private QueryTripsResult queryTrips(NetworkProvider provider, String from, String to, char product, Date plannedDepartureTime) throws IOException {
        char[] products = {product};
        return provider.queryTrips(new Location(LocationType.STATION, from), null, new Location(LocationType.STATION, to), plannedDepartureTime, true, Product.fromCodes(products), null, null, null, null);
    }

    private Date plannedDepartureTime(int timeOffset) {
        return new Date(System.currentTimeMillis() + timeOffset * 60L * 1000L);
    }

    private List<TripData> filterTrips(List<Trip> trips, String from, String to, String mode, Date plannedDepartureTime) {
        List<TripData> list = new ArrayList<>();
        for (Trip trip : trips) {
            Trip.Public leg = trip.getFirstPublicLeg();

            if (leg != null) {
                Date departureTime = leg.getDepartureTime();
                if (departureTime != null && departureTime.after(plannedDepartureTime) && from.equals(leg.departure.id) && to.equals(leg.arrival.id) && !leg.departureStop.departureCancelled) {
                    TripData data = new TripData();
                    data.setFrom(trip.from.name);
                    data.setFromId(trip.from.id);
                    data.setTo(trip.to.name);
                    data.setToId(trip.to.id);
                    data.setProduct(leg.line.product.toString());
                    data.setNumber(leg.line.label);

                    Date plannedTime = leg.departureStop.plannedDepartureTime != null ? leg.departureStop.plannedDepartureTime : departureTime;
                    data.setPlannedDepartureTime(format(plannedTime));
                    data.setPlannedDepartureTimestamp(plannedTime.getTime());

                    Long departureDelay = leg.departureStop.getDepartureDelay();
                    long departureDelaySeconds = departureDelay != null ? departureDelay / 1000 : 0;
                    Date predictedDepartureTime = leg.departureStop.predictedDepartureTime != null ? leg.departureStop.predictedDepartureTime : departureTime;

                    if (mode.equals("esp") && departureDelaySeconds >= 60) {
                        Date correctedTime = new Date(predictedDepartureTime.getTime() - 60000);
                        data.setDepartureTime(format(correctedTime));
                        data.setDepartureTimestamp(correctedTime.getTime());
                    } else {
                        data.setDepartureTime(format(predictedDepartureTime));
                        data.setDepartureTimestamp(predictedDepartureTime.getTime());
                    }

                    data.setDepartureDelay(departureDelaySeconds);
                    list.add(data);
                }
            }
        }
        return list;
    }

    private List<TripData> findMoreTrips(QueryTripsContext context, String from, String to, String mode, NetworkProvider provider, Date plannedDepartureTime) {
        List<TripData> data = new ArrayList<>();
        QueryTripsContext newContext = context;
        int count = 0;
        try {
            while (data.isEmpty() && newContext != null && count < 3) {
                QueryTripsResult efaData = provider.queryMoreTrips(newContext, true);
                newContext = efaData.context;
                data = filterTrips(efaData.trips, from, to, mode, plannedDepartureTime);
                count++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }

    private String toEspResponse(TripData data) {
        return "{\"connections\":[{\"from\":{\"departureTime\":\"" + data.getDepartureTime() + "\",\"plannedDepartureTimestamp\":" + data.getPlannedDepartureTimestamp() + ",\"delay\":" + data.getDepartureDelay() / 60 + ",\"to\": \"" + data.getTo() + "\" }}]}";
    }

    private String format(Date date) {
        return DATE_FORMAT.format(date.toInstant());
    }

    private ResponseEntity<String> providerNotFound(String providerName) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Provider " + providerName + " not found or can not instantiated...");
    }
}
