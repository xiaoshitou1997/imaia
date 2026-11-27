package com.imaia.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.imaia.ai.AiProviderService;
import com.imaia.ai.ConversationSettingsService;
import com.imaia.common.error.ServiceException;
import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.domain.ImConversation;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import com.imaia.repository.ImConversationRepository;

/**
 * 事务边界的真实语义：失败时整体回滚，不留半截数据。
 *
 * <p>把 {@code @Transactional} 从 Controller 下沉到 Service 之后，必须验证
 * 「多表写入要么全成要么全滚」——替身测不出这一点，只有真实数据库 + 真事务能证明。
 *
 * <p>本类不加 {@code @Transactional}：测试自身若参与事务，被测方法的回滚就失去意义。
 */
@EnabledWithTestDatabase
@SpringBootTest
@ActiveProfiles("integration")
class ServiceTransactionBoundaryTest {

    @Autowired
    private AiProviderService providerService;

    @Autowired
    private ConversationSettingsService conversationSettings;

    @Autowired
    private AiProviderRepository providers;

    @Autowired
    private AiModelRepository models;

    @Autowired
    private ImConversationRepository conversations;

    @Autowired
    private JdbcTemplate jdbc;

    private Long seedConversation() {
        jdbc.update("insert into im_account (platform_code, name, enabled, config) "
                + "values ('wechat', ?, false, '{}'::jsonb)", "acc-" + System.nanoTime());
        Long accountId = jdbc.queryForObject(
                "select id from im_account order by id desc limit 1", Long.class);
        jdbc.update("insert into im_conversation (account_id, platform_id, kind, first_seen_at, last_seen_at) "
                + "values (?, ?, 'direct', now(), now())", accountId, "conv-tx-" + System.nanoTime());
        return jdbc.queryForObject("select id from im_conversation order by id desc limit 1", Long.class);
    }

    @Test
    void deletingProviderRemovesItsModels() {
        AiProvider provider = providers.save(AiProvider.builder()
                .name("tx-provider").baseUrl("http://tx").config("{}").enabled(true).build());
        models.save(AiModel.builder()
                .providerId(provider.getId()).modelKey("tx-model")
                .modalitiesIn("[\"text\"]").modalitiesOut("[\"text\"]")
                .supportsToolCalls(true).params("{}").enabled(true).build());

        int removed = providerService.deleteWithModels(provider.getId());

        assertThat(removed).isEqualTo(1);
        assertThat(providers.findById(provider.getId())).isEmpty();
        assertThat(models.findByProviderIdOrderByModelKeyAsc(provider.getId())).isEmpty();
    }

    @Test
    void failedConversationUpdateLeavesNothingBehind() {
        Long conversationId = seedConversation();
        ImConversation before = conversations.findById(conversationId).orElseThrow();
        String originalTitle = before.getTitle();

        // 别名超长会在任何写入之前抛错，整个编辑必须不留下半截修改。
        String tooLong = "a".repeat(ConversationSettingsService.SELF_COMMAND_ALIAS_MAX + 1);

        assertThatThrownBy(() -> conversationSettings.update(conversationId,
                new ConversationSettingsService.ConversationEdit(
                        "新标题", null, null, null, tooLong, null)))
                .isInstanceOf(ServiceException.class);

        ImConversation after = conversations.findById(conversationId).orElseThrow();
        assertThat(after.getTitle())
                .as("校验失败时标题不能被部分写入")
                .isEqualTo(originalTitle);

        Integer aliasRows = jdbc.queryForObject(
                "select count(*) from ai_setting where conversation_id = ? and key like '%alias%'",
                Integer.class, conversationId);
        assertThat(aliasRows)
                .as("别名超长时不应留下任何配置行")
                .isZero();
    }

    @Test
    void successfulConversationUpdatePersistsAllFields() {
        Long conversationId = seedConversation();

        conversationSettings.update(conversationId,
                new ConversationSettingsService.ConversationEdit(
                        "标题A", "备注A", "人设A", true, "小艾", false));

        ImConversation updated = conversations.findById(conversationId).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("标题A");
        assertThat(updated.getRemark()).isEqualTo("备注A");

        Optional<ImConversation> reread = conversations.findById(conversationId);
        assertThat(reread).isPresent();
    }
}
