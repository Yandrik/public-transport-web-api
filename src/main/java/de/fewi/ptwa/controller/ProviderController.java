package de.fewi.ptwa.controller;

import de.fewi.ptwa.entity.Provider;
import de.fewi.ptwa.util.ProviderUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@RestController
public class ProviderController {

    @GetMapping("/provider")
    public ResponseEntity<List<Provider>> providerlist() throws IOException {
        List<Provider> list = new ArrayList<>();

        for (ProviderUtil.ProviderInfo providerInfo : ProviderUtil.getAvailableProviders()) {
            Provider provider = new Provider();
            provider.setName(providerInfo.id());
            provider.setClass(providerInfo.className());
            list.add(provider);
        }
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(list);
    }

}
