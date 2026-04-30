package de.fewi.ptwa.controller;

import de.fewi.ptwa.entity.DepartureData;
import de.fewi.ptwa.util.ProviderUtil;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class DepartureController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmZ")
            .withZone(ZoneId.systemDefault());

    private final AtomicInteger counter = new AtomicInteger();

    @Value("${thingspeak.key:}")
    private String thingspeakKey;

    @Value("${thingspeak.channel:field1}")
    private String thingspeakChannel;

    @GetMapping("/departure")
    public ResponseEntity<?> departure(@RequestParam("from") String from, @RequestParam(value = "provider", required = false) String providerName, @RequestParam(value = "limit", defaultValue = "10") int limit) throws IOException {
        try {
            NetworkProvider provider = getNetworkProvider(providerName);
            if (provider == null) {
                return providerNotFound(providerName);
            }
            QueryDeparturesResult efaData = provider.queryDepartures(from, new Date(), 120, true);
            if (efaData.status.name().equals("OK")) {
                List<DepartureData> list = new ArrayList<>();
                if (efaData.findStationDepartures(from) == null && !efaData.stationDepartures.isEmpty()) {
                    for (StationDepartures stationDeparture : efaData.stationDepartures) {
                        list.addAll(convertDepartures(stationDeparture));
                    }
                    Collections.sort(list);
                } else {
                    list.addAll(convertDepartures(efaData.findStationDepartures(from)));
                }
                if(list.size() > limit)
                    list = list.subList(0,limit);
                return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(list);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EFA error status: " + efaData.status.name());
        }finally
        {
            counter.incrementAndGet();
        }
    }

    @GetMapping("/departureFHEM")
    public ResponseEntity<?> departureFHEM(@RequestParam("from") String from, @RequestParam(value = "provider", required = false) String providerName, @RequestParam(value = "limit", defaultValue = "10") int limit) throws IOException {
        try {
            NetworkProvider provider = getNetworkProvider(providerName);
            if (provider == null) {
                return providerNotFound(providerName);
            }
            QueryDeparturesResult efaData = provider.queryDepartures(from, new Date(), 120, true);
            if (efaData.status.name().equals("OK")) {
                String data;
                if (efaData.findStationDepartures(from) == null && !efaData.stationDepartures.isEmpty()) {
                    List<DepartureData> list = new ArrayList<>();
                    for (StationDepartures stationDeparture : efaData.stationDepartures) {
                        list.addAll(convertDepartures(stationDeparture));
                    }
                    Collections.sort(list);
                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    int count = 0;
                    for (DepartureData departureData : list) {
                        sb.append("[\"").append(departureData.getNumber()).append("\",\"").append(departureData.getTo()).append("\",\"").append(departureData.getDepartureTimeInMinutes()).append("\"],");
                        count++;
                        if(count >= limit)
                            break;
                    }
                    data = trimTrailingCommaArray(sb);
                } else {
                    data = convertDeparturesFHEM(efaData.findStationDepartures(from), limit);
                }
                return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(data);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EFA error status: " + efaData.status.name());
        }finally {
            counter.incrementAndGet();
        }
    }

    @Scheduled(initialDelay=10000, fixedRate=300000)
    public void doSomething() {
        if(thingspeakKey == null || thingspeakKey.isEmpty())
            return;
        String url = "http://api.thingspeak.com/update?key=";
        url += thingspeakKey;
        url += "&"+thingspeakChannel+"=";
        url += counter.getAndSet(0);
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.getResponseCode();
        }catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    private NetworkProvider getNetworkProvider(String providerName) {
        return ProviderUtil.getObjectForProvider(providerName);
    }

    private String convertDeparturesFHEM(StationDepartures stationDepartures, int limit) {
        if (stationDepartures == null || stationDepartures.departures.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        Calendar cal = Calendar.getInstance();
        int count = 0;
        for (Departure departure : stationDepartures.departures) {
            Date departureTime = departure.getTime();
            long time = departureTime.getTime() - cal.getTimeInMillis();
            float depMinutes = (float)time / 1000 / 60;
            String destination = departure.destination != null ? departure.destination.name : "";
            sb.append("[\"").append(departure.line.label).append("\",\"").append(destination).append("\",\"").append((int)Math.ceil(depMinutes)).append("\"],");
            count++;
            if(count >= limit)
                break;
        }
        return trimTrailingCommaArray(sb);
    }

    private List<DepartureData> convertDepartures(StationDepartures stationDepartures) {
        Calendar cal = Calendar.getInstance();
        List<DepartureData> list = new ArrayList<>();
        if (stationDepartures == null) {
            return list;
        }

        for (Departure departure : stationDepartures.departures) {
            DepartureData data = new DepartureData();
            if (departure.destination != null) {
                data.setTo(departure.destination.name);
                data.setToId(departure.destination.id);
            }
            data.setProduct(departure.line.product.toString());
            data.setNumber(departure.line.label);
            if (departure.position != null)
                data.setPlatform(departure.position.name);

            Date departureTime = departure.getTime();
            data.setDepartureTime(format(departureTime));
            data.setDepartureTimestamp(departureTime.getTime());
            if (departure.predictedTime != null && departure.plannedTime != null && departure.predictedTime.after(departure.plannedTime)) {
                data.setDepartureDelay((departure.predictedTime.getTime() - departure.plannedTime.getTime()) / 1000 / 60);
            }

            long time = departureTime.getTime() - cal.getTimeInMillis();
            float depMinutes = (float)time / 1000 / 60;
            data.setDepartureTimeInMinutes((int) Math.ceil(depMinutes));
            list.add(data);
        }
        return list;
    }

    private String trimTrailingCommaArray(StringBuilder sb) {
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("]");
        return sb.toString();
    }

    private String format(Date date) {
        return DATE_FORMAT.format(date.toInstant());
    }

    private ResponseEntity<String> providerNotFound(String providerName) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Provider " + providerName + " not found or can not instantiated...");
    }
}
