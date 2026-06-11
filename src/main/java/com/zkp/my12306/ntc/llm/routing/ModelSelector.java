package com.zkp.my12306.ntc.llm.routing;

import com.zkp.my12306.ntc.llm.config.AIModelProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ModelSelector {
    private static final Logger log = LoggerFactory.getLogger(ModelSelector.class);
    private static final Set<String> API_KEY_REQUIRED_PROVIDERS = Set.of(
            "siliconflow", "deepseek", "openai", "bailian");

    private final AIModelProperties properties;
    private final ModelHealthStore healthStore;

    public ModelSelector(AIModelProperties properties, ModelHealthStore healthStore) {
        this.properties = properties;
        this.healthStore = healthStore;
    }

    @PostConstruct
    void logChatModelConfig() {
        AIModelProperties.ModelGroup group = properties.getChat();
        if (group == null) {
            log.warn("未配置 ai.chat 模型组");
            return;
        }
        List<ModelTarget> targets = selectChatCandidates();
        String order = targets.stream()
                .map(target -> target.id() + "(" + target.candidate().getProvider() + ")")
                .collect(Collectors.joining(" -> "));
        log.info("大模型路由配置: default-model={}, 可用顺序={}",
                group.getDefaultModel(), order.isBlank() ? "无" : order);
    }

    public List<ModelTarget> selectChatCandidates() {
        AIModelProperties.ModelGroup group = properties.getChat();
        if (group == null) {
            return List.of();
        }
        return selectCandidates(group, group.getDefaultModel());
    }

    private List<ModelTarget> selectCandidates(AIModelProperties.ModelGroup group, String firstChoiceModelId) {
        if (group.getCandidates() == null) {
            return List.of();
        }

        List<AIModelProperties.ModelCandidate> orderedCandidates =
                filterAndSortCandidates(group.getCandidates(), firstChoiceModelId);
        return buildAvailableTargets(orderedCandidates);
    }

    private List<AIModelProperties.ModelCandidate> filterAndSortCandidates(
            List<AIModelProperties.ModelCandidate> candidates,
            String firstChoiceModelId) {
        return candidates.stream()
                .filter(candidate -> candidate != null && !Boolean.FALSE.equals(candidate.getEnabled()))
                .sorted(Comparator
                        .comparing((AIModelProperties.ModelCandidate candidate) ->
                                !Objects.equals(resolveId(candidate), firstChoiceModelId))
                        .thenComparing(AIModelProperties.ModelCandidate::getPriority,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AIModelProperties.ModelCandidate::getId,
                                Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    private List<ModelTarget> buildAvailableTargets(List<AIModelProperties.ModelCandidate> candidates) {
        Map<String, AIModelProperties.ProviderConfig> providers = properties.getProviders();
        return candidates.stream()
                .map(candidate -> buildModelTarget(candidate, providers))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ModelTarget buildModelTarget(
            AIModelProperties.ModelCandidate candidate,
            Map<String, AIModelProperties.ProviderConfig> providers) {
        String modelId = resolveId(candidate);
        if (healthStore.isUnavailable(modelId)) {
            log.warn("模型处于熔断状态，跳过: modelId={}", modelId);
            return null;
        }
        AIModelProperties.ProviderConfig provider = providers.get(candidate.getProvider());
        if (provider == null) {
            log.warn("Provider配置缺失: provider={}, modelId={}", candidate.getProvider(), modelId);
            return null;
        }
        if (requiresApiKey(candidate.getProvider()) && isBlank(provider.getApiKey())) {
            log.warn("Provider 缺少 api-key，跳过: provider={}, modelId={}", candidate.getProvider(), modelId);
            return null;
        }
        validateTarget(modelId, candidate, provider);
        return new ModelTarget(modelId, candidate, provider);
    }

    private void validateTarget(
            String modelId,
            AIModelProperties.ModelCandidate candidate,
            AIModelProperties.ProviderConfig provider) {
        if (candidate.getProvider() == null || candidate.getProvider().isBlank()) {
            throw new IllegalStateException("模型配置缺少 provider：" + modelId);
        }
        if (candidate.getModel() == null || candidate.getModel().isBlank()) {
            throw new IllegalStateException("模型配置缺少 model：" + modelId);
        }
        boolean hasCandidateUrl = candidate.getUrl() != null && !candidate.getUrl().isBlank();
        boolean hasProviderUrl = provider.getUrl() != null && !provider.getUrl().isBlank();
        if (!hasCandidateUrl && !hasProviderUrl) {
            throw new IllegalStateException("模型配置缺少 url：" + modelId);
        }
    }

    private boolean requiresApiKey(String provider) {
        return provider != null && API_KEY_REQUIRED_PROVIDERS.contains(provider);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveId(AIModelProperties.ModelCandidate candidate) {
        if (candidate.getId() != null && !candidate.getId().isBlank()) {
            return candidate.getId();
        }
        return String.format("%s::%s",
                Objects.toString(candidate.getProvider(), "unknown"),
                Objects.toString(candidate.getModel(), "unknown"));
    }
}
