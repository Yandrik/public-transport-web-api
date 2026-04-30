/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.fewi.ptwa.controller.v2.model;

import de.fewi.ptwa.controller.v2.PropertyReader;
import de.fewi.ptwa.util.ProviderUtil;
import de.schildbach.pte.NetworkProvider;

/**
 *
 * @author constantin
 */
public enum ProviderEnum {

    KVV("Kvv"), BVG("Bvg"), VBB("Vbb");
    
    private final String providerName;

    ProviderEnum(String providerName) {
        this.providerName = providerName;
    }

    public String label() {
        return PropertyReader.INSTANCE.getProperty("de/fewi/ptwa/controller/v2/provider.properties", this.name().toLowerCase(), this.name());
    }

    public NetworkProvider newNetworkProvider() {
        NetworkProvider networkProvider = ProviderUtil.getObjectForProvider(providerName);
        if (networkProvider == null) {
            throw new RuntimeException("error on instantiation of networkprovider '" + name() + "'");
        }
        return networkProvider;
    }
    
    public Provider asProvider() {
        Provider provider = new Provider();
        provider.setDescription(this.label());
        provider.setName(this.name());                
        return provider;
    }

}
