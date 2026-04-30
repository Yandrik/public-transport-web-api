package de.fewi.ptwa.controller.v2.model;

import de.schildbach.pte.BvgProvider;
import de.schildbach.pte.KvvProvider;
import de.schildbach.pte.VbbProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ProviderEnumTest {

    @Test
    void createsBvgProvider() {
        assertInstanceOf(BvgProvider.class, ProviderEnum.BVG.newNetworkProvider());
        assertEquals("Berliner Verkehrsgesellschaft", ProviderEnum.BVG.label());
    }

    @Test
    void createsKvvProvider() {
        assertInstanceOf(KvvProvider.class, ProviderEnum.KVV.newNetworkProvider());
        assertEquals("Karlsruher Verkehrsverbund", ProviderEnum.KVV.label());
    }

    @Test
    void createsVbbProvider() {
        assertInstanceOf(VbbProvider.class, ProviderEnum.VBB.newNetworkProvider());
        assertEquals("Verkehrsverbund Berlin Brandenburg", ProviderEnum.VBB.label());
    }
}
