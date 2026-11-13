const fs = require('fs');
const repo = 'D:/code/github/imaia/imaia-service/src/main/java/com/imaia/';
const src = 'C:/Users/xiaoshitou/AppData/Local/hermes/cache/scratch/imaia-v021/';

// ---------- 1) call-site transforms ----------
const transforms = [
  ['ai/OpenAiChatCompletion.java', [
    ['import com.imaia.config.ModelRegistry;', 'import com.imaia.config.AiModelCatalog;', 1],
    ['import com.imaia.domain.ModelCallLog;', 'import com.imaia.domain.AiCallLog;', 1],
    ['import com.imaia.repository.ModelCallLogRepository;', 'import com.imaia.repository.AiCallLogRepository;', 1],
    ['    private final ModelRegistry registry;', '    private final AiModelCatalog catalog;', 1],
    ['    public OpenAiChatCompletion(ChatModelFactory chatModels, ModelRegistry registry,\n            ModelCallLogRepository callLogs) {',
     '    public OpenAiChatCompletion(ChatModelFactory chatModels, AiModelCatalog catalog,\n            AiCallLogRepository callLogs) {', 1],
    ['        this.registry = registry;', '        this.catalog = catalog;', 1],
    ['        ModelRegistry.Resolved brain = registry.chatFor(image != null);',
     '        AiModelCatalog.Resolved brain = catalog.chatFor(image != null);', 1],
    ['            callLogs.save(new ModelCallLog(null, null, "chat", brain.providerName(), brain.id(),',
     '            callLogs.save(new AiCallLog(null, null, "chat", brain.providerName(), brain.id(),', 1],
  ]],
  ['ai/ImageGenerationClient.java', [
    ['import com.imaia.config.ModelRegistry;', 'import com.imaia.config.AiModelCatalog;', 1],
    ['    private final ModelRegistry registry;', '    private final AiModelCatalog catalog;', 1],
    ['    public ImageGenerationClient(ModelRegistry registry) {\n        this.registry = registry;',
     '    public ImageGenerationClient(AiModelCatalog catalog) {\n        this.catalog = catalog;', 1],
    ['        ModelRegistry.Resolved image = registry.byProtocol(ModelRegistry.PROTOCOL_IMAGES);',
     '        AiModelCatalog.Resolved image = catalog.imageGeneration();', 1],
  ]],
  ['ai/VideoGenerationClient.java', [
    ['import com.imaia.config.ModelRegistry;', 'import com.imaia.config.AiModelCatalog;', 1],
    ['    private final ModelRegistry registry;', '    private final AiModelCatalog catalog;', 1],
    ['    public VideoGenerationClient(ModelRegistry registry) {\n        this.registry = registry;',
     '    public VideoGenerationClient(AiModelCatalog catalog) {\n        this.catalog = catalog;', 1],
    ['        ModelRegistry.Resolved video = registry.byProtocol(ModelRegistry.PROTOCOL_VIDEO);',
     '        AiModelCatalog.Resolved video = catalog.videoGeneration();', 1],
    ['    private String createTask(ModelRegistry.Resolved video, String prompt) {',
     '    private String createTask(AiModelCatalog.Resolved video, String prompt) {', 1],
    ['    private JsonNode pollUntilDone(ModelRegistry.Resolved video, String taskId, long deadline) {',
     '    private JsonNode pollUntilDone(AiModelCatalog.Resolved video, String taskId, long deadline) {', 1],
  ]],
  ['ai/AiReplyHandler.java', [
    ['import com.imaia.config.ModelRegistry;', 'import com.imaia.config.AiModelCatalog;', 1],
    ['    private final ModelRegistry modelRegistry;', '    private final AiModelCatalog catalog;', 1],
    ['            AssistantSettingsService settings, ModelRegistry modelRegistry,',
     '            AssistantSettingsService settings, AiModelCatalog catalog,', 1],
    ['        this.modelRegistry = modelRegistry;', '        this.catalog = catalog;', 1],
    ['modelRegistry.chatSupportsImages()', 'catalog.hasVisionChat()', 3],
  ]],
  ['ai/InsightService.java', [
    ['import com.imaia.config.ModelRegistry;', 'import com.imaia.config.AiModelCatalog;', 1],
    ['    private final ModelRegistry registry;', '    private final AiModelCatalog catalog;', 1],
    ['    public InsightService(ImMessageRepository messages, ConversationReportRepository reports,\n            OpenAiChatCompletion chatCompletion, ModelRegistry registry) {',
     '    public InsightService(ImMessageRepository messages, ConversationReportRepository reports,\n            OpenAiChatCompletion chatCompletion, AiModelCatalog catalog) {', 1],
    ['        this.registry = registry;', '        this.catalog = catalog;', 1],
    ['            modelId = registry.chatFor(false).id();', '            modelId = catalog.chatFor(false).id();', 1],
    ['        report.setCreatedAt(Instant.now());', '        report.setGeneratedAt(Instant.now());', 1],
  ]],
  ['adapter/ImAdapter.java', [
    ['    record FetchedMessage(String conversationExternalId, String messageId, String senderId,\n            String senderName, String direction, Integer providerType, MessageCategory category,',
     '    record FetchedMessage(String conversationExternalId, String messageId, String senderId,\n            String senderName, String direction, Integer platformType, MessageCategory category,', 1],
  ]],
  ['handler/MessageArchiveHandler.java', [
    ['fetched.providerType()', 'fetched.platformType()', 1],
  ]],
];
let failures = 0;
for (const [rel, rules] of transforms) {
  const path = repo + rel.replace('../', '');
  let text = fs.readFileSync(path, 'utf8');
  let ok = true;
  for (const [find, replace, expected] of rules) {
    const count = text.split(find).length - 1;
    if (count !== expected) {
      console.log('ASSERT FAIL ' + rel + ' :: count=' + count + ' expected=' + expected
        + ' :: ' + JSON.stringify(find.slice(0, 70)));
      ok = false;
      failures++;
      break;
    }
  }
  if (!ok) continue;
  for (const [find, replace] of rules) {
    text = text.split(find).join(replace);
  }
  fs.writeFileSync(path, text, 'utf8');
  console.log('transformed ' + rel);
}

// ---------- 2) copy scratch v021 files into the repo ----------
function walkSync(dir, cb) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const path = dir + (dir.endsWith('/') ? '' : '/') + entry.name;
    if (entry.isDirectory()) walkSync(path, cb);
    else cb(path, path.slice(src.length));
  }
}
walkSync(src, (from, rel) => {
  const dst = (rel.endsWith('.sql')
    ? 'D:/code/github/imaia/imaia-service/src/main/resources/db/migration/'
    : repo) + rel;
  fs.mkdirSync(dst.slice(0, dst.lastIndexOf('/')), { recursive: true });
  fs.copyFileSync(from, dst);
  console.log('copied ' + rel);
});

// ---------- 3) delete superseded files ----------
const dead = [
  'domain/Provider.java', 'domain/Model.java', 'domain/PersonAnalysis.java', 'domain/ModelCallLog.java',
  'repository/ProviderRepository.java', 'repository/ModelRepository.java',
  'repository/PersonAnalysisRepository.java', 'repository/ModelCallLogRepository.java',
  'config/ModelRegistry.java', 'ai/PersonAnalyzer.java', 'web/AdminApiController.java',
];
for (const rel of dead) {
  try { fs.unlinkSync(repo + rel); console.log('deleted ' + rel); }
  catch (e) { console.log('skip ' + rel + ': ' + e.message); }
}
console.log(failures === 0 ? 'ALL OK' : failures + ' transform failures');
