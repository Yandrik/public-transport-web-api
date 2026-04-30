package de.fewi.ptwa.controller;

import de.fewi.ptwa.entity.Provider;
import de.schildbach.pte.NetworkProvider;
import org.reflections.Reflections;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@RestController
public class ProviderController {

    @GetMapping("/provider")
    public ResponseEntity<List<Provider>> providerlist() throws IOException {
        List<Provider> list = new ArrayList<>();

        Set<Class<? extends NetworkProvider>> reflection = new Reflections("de.schildbach.pte").getSubTypesOf(NetworkProvider.class);
        for (Class<? extends NetworkProvider> implClass : reflection) {
            if(implClass.getSimpleName().startsWith("Abstract"))
                continue;
            Provider provider = new Provider();
            provider.setName(implClass.getSimpleName().substring(0, implClass.getSimpleName().indexOf("Provider")));
            provider.setClass(implClass.getSimpleName());
            list.add(provider);
        }
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(list);
    }

}
