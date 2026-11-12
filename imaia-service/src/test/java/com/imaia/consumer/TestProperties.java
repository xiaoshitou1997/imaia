package com.imaia.consumer;

import com.imaia.consumer.ai.AssistantProfileService;
import com.imaia.consumer.ai.RiskPolicyService;
import com.imaia.consumer.config.DispatchProperties;
import com.imaia.consumer.config.ModelRegistry;
import com.imaia.consumer.config.WechatAdapterProperties;
import com.imaia.consumer.domain.AssistantProfile;
import com.imaia.consumer.domain.Model;
import com.imaia.consumer.domain.Provider;
import com.imaia.consumer.domain.RiskPolicy;
import com.imaia.consumer.repository.AssistantProfileRepository;
import com.imaia.consumer.repository.ModelRepository;
import com.imaia.consumer.repository.ProviderRepository;
import com.imaia.consumer.repository.RiskPolicyRepository;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Test fixture: entity builders and stubbed-repository services. */
public final class TestProperties {

    private static final AtomicLong IDS = new AtomicLong();


    private static final String QUOTE = "\"";
    private static final String COLON = ":";
    private static final char QC = '"'; // double-quote char
    private TestProperties() {
    }

    public static WechatAdapterProperties wechat(String baseUrl, String account) {
        return new WechatAdapterProperties(true,
                new WechatAdapterProperties.Daemon(baseUrl, "test-token", account, 1000, 5),
                new WechatAdapterProperties.Stream("imaia", true, 10, 50, 200, false));
    }

    public static DispatchProperties dispatch() {
        return new DispatchProperties(3);
    }

    public static Provider provider(String name, String baseUrl) {
        Provider provider = new Provider(name, baseUrl, "key", Instant.now());
        provider.setId(IDS.incrementAndGet());
        return provider;
    }

    public static Model model(Provider provider, String modelId, String name, String protocol,
            List<String> input, List<String> output, Integer maxOutputTokens,
            Map<String, String> params) {
        return new Model(provider.getId(), modelId, name, protocol, 8192,
                json(input), json(output), maxOutputTokens, json(params), Instant.now());
    }

    public static ModelRegistry registry(Provider provider, Model... models) {
        return registry(List.of(provider), List.of(models));
    }

    public static ModelRegistry registry(List<Provider> providers, List<Model> models) {
        ProviderRepository providerRepository = Mockito.mock(ProviderRepository.class);
        ModelRepository modelRepository = Mockito.mock(ModelRepository.class);
        Mockito.when(providerRepository.findByEnabledTrue()).thenReturn(providers);
        Mockito.when(modelRepository.findByEnabledTrue()).thenReturn(models);
        return new ModelRegistry(providerRepository, modelRepository);
    }

    /** The default test directory: agnes image/video/asr, zhipu chat, minimax speech. */
    public static ModelRegistry modelRegistry(String baseUrl) {
        Provider agnes = provider("agnes", baseUrl);
        Provider zhipu = provider("zhipu", baseUrl);
        Provider minimax = provider("minimax", baseUrl);
        return registry(List.of(agnes, zhipu, minimax), List.of(
                model(agnes, "img-m", "Agnes Image 2.5 Flash", "openai-images",
                        List.of("text"), List.of("image"), null, Map.of("size", "1024x768")),
                model(agnes, "vid-m", "Agnes Video 2.5 Flash", "openai-video",
                        List.of("text"), List.of("video"), null,
                        Map.of("seconds", "5", "aspect-ratio", "16:9")),
                model(agnes, "asr-m", "Whisper", "openai-audio-transcription",
                        List.of("audio"), List.of("text"), null, Map.of()),
                model(zhipu, "chat-m", "GLM-4.5V", "openai-completions",
                        List.of("text", "image"), List.of("text"), 4096, Map.of()),
                model(minimax, "speech-m", "MiniMax Speech-02-Turbo", "openai-audio-speech",
                        List.of("text"), List.of("audio"), null, Map.of("voice-id", "tongtong"))));
    }

    /** The default directory minus every model of one protocol. */
    public static ModelRegistry modelRegistryWithout(String baseUrl, String protocol) {
        Provider agnes = provider("agnes", baseUrl);
        Provider zhipu = provider("zhipu", baseUrl);
        Provider minimax = provider("minimax", baseUrl);
        List<Model> all = List.of(
                model(agnes, "img-m", "Agnes Image 2.5 Flash", "openai-images",
                        List.of("text"), List.of("image"), null, Map.of("size", "1024x768")),
                model(agnes, "vid-m", "Agnes Video 2.5 Flash", "openai-video",
                        List.of("text"), List.of("video"), null,
                        Map.of("seconds", "5", "aspect-ratio", "16:9")),
                model(agnes, "asr-m", "Whisper", "openai-audio-transcription",
                        List.of("audio"), List.of("text"), null, Map.of()),
                model(zhipu, "chat-m", "GLM-4.5V", "openai-completions",
                        List.of("text", "image"), List.of("text"), 4096, Map.of()),
                model(minimax, "speech-m", "MiniMax Speech-02-Turbo", "openai-audio-speech",
                        List.of("text"), List.of("audio"), null, Map.of("voice-id", "tongtong")));
        return registry(List.of(agnes, zhipu, minimax),
                all.stream().filter(model -> !protocol.equals(model.getProtocol())).toList());
    }

    public static AssistantProfile profile(String systemPrompt, int historyLimit, String replyThreads,
            boolean requireGroupMention, boolean ttsReply, int minMessageLength,
            long replyDelayMinMs, long replyDelayMaxMs) {
        return new AssistantProfile(systemPrompt, historyLimit, replyThreads, requireGroupMention,
                ttsReply, minMessageLength, replyDelayMinMs, replyDelayMaxMs, true);
    }

    public static RiskPolicy risk(int repliesPerThreadPerMinute, int maxRepliesPerHour,
            int maxRepliesPerDay, String quietHoursStart, String quietHoursEnd) {
        return new RiskPolicy(repliesPerThreadPerMinute, maxRepliesPerHour, maxRepliesPerDay,
                quietHoursStart, quietHoursEnd);
    }

    public static AssistantProfileService profileService(AssistantProfile profile) {
        AssistantProfileRepository repository = Mockito.mock(AssistantProfileRepository.class);
        Mockito.when(repository.findById(1)).thenReturn(java.util.Optional.of(profile));
        Mockito.when(repository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        return new AssistantProfileService(repository);
    }

    public static RiskPolicyService riskService(RiskPolicy policy) {
        RiskPolicyRepository repository = Mockito.mock(RiskPolicyRepository.class);
        Mockito.when(repository.findById(1)).thenReturn(java.util.Optional.of(policy));
        Mockito.when(repository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        return new RiskPolicyService(repository);
    }

    public static AssistantProfileRepository profileRepository(AssistantProfile profile) {
        AssistantProfileRepository repository = Mockito.mock(AssistantProfileRepository.class);
        Mockito.when(repository.findById(1)).thenReturn(java.util.Optional.of(profile));
        Mockito.when(repository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        return repository;
    }

    public static RiskPolicyRepository riskRepository(RiskPolicy policy) {
        RiskPolicyRepository repository = Mockito.mock(RiskPolicyRepository.class);
        Mockito.when(repository.findById(1)).thenReturn(java.util.Optional.of(policy));
        Mockito.when(repository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        return repository;
    }

    public static String json(List<String> list) {
        String inner = list.stream().map(value -> QC + value + QC).collect(Collectors.joining(","));
        return "[" + inner + "]";
    }

    public static String json(Map<String, String> map) {
        String inner = map.entrySet().stream()
                .map(entry -> QC + entry.getKey() + QC + ':' + QC + entry.getValue() + QC)
                .collect(Collectors.joining(","));
        return "{" + inner + "}";
    }
}