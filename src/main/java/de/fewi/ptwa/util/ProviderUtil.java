package de.fewi.ptwa.util;

import de.schildbach.pte.NetworkProvider;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ProviderUtil {

    public record ProviderInfo(String id, String providerName, String className) {
    }

    private static String defaultProviderName = "Kvv";

    private static Environment environment;

    public static NetworkProvider getObjectForProvider(String providerName) {
        String effectiveProviderName = providerName;
        if (effectiveProviderName == null || effectiveProviderName.isBlank()) {
            effectiveProviderName = defaultProviderName;
        }
        if(effectiveProviderName == null || effectiveProviderName.length() < 1)
            return null;
        ProviderInfo providerInfo = findProviderInfo(effectiveProviderName);
        if (providerInfo == null) {
            return null;
        }
        try {
            Class<?> providerClass = Class.forName("de.schildbach.pte." + providerInfo.className());
            return instantiateProvider(providerClass, providerInfo);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static List<ProviderInfo> getAvailableProviders() {
        Set<Class<? extends NetworkProvider>> providerClasses = new Reflections("de.schildbach.pte").getSubTypesOf(NetworkProvider.class);
        List<ProviderInfo> providers = new ArrayList<>();
        for (Class<? extends NetworkProvider> providerClass : providerClasses) {
            if (providerClass.isInterface() || Modifier.isAbstract(providerClass.getModifiers())) {
                continue;
            }
            String className = providerClass.getSimpleName();
            if (!className.endsWith("Provider")) {
                continue;
            }
            String providerName = className.substring(0, className.length() - "Provider".length());
            providers.add(new ProviderInfo(toProviderId(providerName), providerName, className));
        }
        providers.sort(Comparator.comparing(ProviderInfo::id));
        return providers;
    }

    private static ProviderInfo findProviderInfo(String providerName) {
        String normalizedInput = normalizeProviderKey(providerName);
        for (ProviderInfo provider : getAvailableProviders()) {
            if (normalizeProviderKey(provider.id()).equals(normalizedInput)
                    || normalizeProviderKey(provider.providerName()).equals(normalizedInput)) {
                return provider;
            }
        }
        return null;
    }

    private static NetworkProvider instantiateProvider(Class<?> providerClass, ProviderInfo providerInfo) throws ReflectiveOperationException {
        Constructor<?> noArgsConstructor = findConstructor(providerClass);
        if (noArgsConstructor != null) {
            return (NetworkProvider) noArgsConstructor.newInstance();
        }

        Constructor<?> stringConstructor = findConstructor(providerClass, String.class);
        if (stringConstructor != null) {
            return (NetworkProvider) stringConstructor.newInstance(getProviderKey(providerInfo));
        }

        Constructor<?> bytesConstructor = findConstructor(providerClass, byte[].class);
        if (bytesConstructor != null) {
            return (NetworkProvider) bytesConstructor.newInstance(getProviderKey(providerInfo).getBytes(StandardCharsets.UTF_8));
        }

        return null;
    }

    private static Constructor<?> findConstructor(Class<?> providerClass, Class<?>... parameterTypes) {
        try {
            return providerClass.getDeclaredConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String getProviderKey(ProviderInfo providerInfo) {
        if (environment == null) {
            return "";
        }
        for (String propertyName : providerKeyPropertyNames(providerInfo)) {
            String value = environment.getProperty(propertyName);
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private static List<String> providerKeyPropertyNames(ProviderInfo providerInfo) {
        String id = providerInfo.id().toLowerCase(Locale.ROOT);
        String providerName = providerInfo.providerName().toLowerCase(Locale.ROOT);
        return List.of(
                "providerkey." + id,
                "providerkey." + id.replace('_', '-'),
                "providerkey." + id.replace('_', '.'),
                "providerkey." + providerName,
                "PROVIDERKEY_" + providerInfo.id()
        );
    }

    private static String toProviderId(String providerName) {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < providerName.length(); i++) {
            char current = providerName.charAt(i);
            if (i > 0 && Character.isUpperCase(current) && Character.isLowerCase(providerName.charAt(i - 1))) {
                id.append('_');
            }
            id.append(Character.toUpperCase(current));
        }
        return id.toString();
    }

    private static String normalizeProviderKey(String providerName) {
        return providerName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    @Value("${provider.default:Kvv}")
    public void setDefaultProviderName(String defaultProviderName) {
        ProviderUtil.defaultProviderName = defaultProviderName;
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        ProviderUtil.environment = environment;
    }
}
