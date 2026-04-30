package de.fewi.ptwa.util;

import de.schildbach.pte.NetworkProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProviderUtil {

    private static String defaultProviderName = "Kvv";

    private static String bvgKey = "";

    private static String vbbKey = "";

    public static NetworkProvider getObjectForProvider(String providerName) {
        String effectiveProviderName = providerName;
        if (effectiveProviderName == null || effectiveProviderName.isBlank()) {
            effectiveProviderName = defaultProviderName;
        }
        if(effectiveProviderName == null || effectiveProviderName.length() < 1)
            return null;
        try {
            Class<?> providerClass = Class.forName("de.schildbach.pte." + normalizeProviderName(effectiveProviderName) + "Provider");
            if (!NetworkProvider.class.isAssignableFrom(providerClass)) {
                return null;
            }
            if(effectiveProviderName.equalsIgnoreCase("Bvg"))
            {
                return  (NetworkProvider)providerClass.getDeclaredConstructor(String.class).newInstance(bvgKey);
            }
            if(effectiveProviderName.equalsIgnoreCase("Vbb"))
            {
                return  (NetworkProvider)providerClass.getDeclaredConstructor(String.class).newInstance(vbbKey);
            }
            return (NetworkProvider)providerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String normalizeProviderName(String providerName) {
        if (providerName.length() == 1) {
            return providerName.toUpperCase();
        }
        if (providerName.equals(providerName.toUpperCase())) {
            return providerName.substring(0, 1).toUpperCase() + providerName.substring(1).toLowerCase();
        }
        return providerName.substring(0, 1).toUpperCase() + providerName.substring(1);
    }

    @Value("${provider.default:Kvv}")
    public void setDefaultProviderName(String defaultProviderName) {
        ProviderUtil.defaultProviderName = defaultProviderName;
    }

    @Value("${providerkey.bvg:}")
    public void setBvgKey(String bvgKey) {
        ProviderUtil.bvgKey = bvgKey;
    }

    @Value("${providerkey.vbb:}")
    public void setVbbKey(String vbbKey) {
        ProviderUtil.vbbKey = vbbKey;
    }
}
