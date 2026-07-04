package com.richardsand.novelkms.ai;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor()
public class AiProviderRegistry {
    @Getter
    final Map<String, AiProvider> aiProviderMap = new HashMap<>();
    
    public static AiProviderRegistry create(AiProvider... providers) {
        AiProviderRegistry reg = new AiProviderRegistry();
        reg.add(providers);
        return reg;
    }

    public void add(AiProvider... providers) {
        for (AiProvider provider : providers) {
            aiProviderMap.put(provider.providerKey(), provider);
        }
    }

    public AiProvider get(String key) {
        return aiProviderMap.get(key);
    }

    public Collection<AiProvider> getProviders() {
        return aiProviderMap.values();
    }

    public Set<String> getKeys() {
        return aiProviderMap.keySet();
    }

    public void clear() {
        aiProviderMap.clear();
    }
}
