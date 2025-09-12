package io.bdeploy.minion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.simpl.RAMJobStore;
import org.quartz.simpl.SimpleThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.LockableDatabase;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TaskSynchronizer;
import io.bdeploy.common.Version;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.manifest.SettingsManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.interfaces.settings.Auth0SettingsDto;
import io.bdeploy.interfaces.settings.MailReceiverSettingsDto;
import io.bdeploy.interfaces.settings.MailSenderSettingsDto;
import io.bdeploy.interfaces.settings.OIDCSettingsDto;
import io.bdeploy.interfaces.settings.OktaSettingsDto;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.jersey.JerseySessionConfiguration;
import io.bdeploy.jersey.SessionStorage;
import io.bdeploy.jersey.actions.Action;
import io.bdeploy.jersey.actions.ActionExecution;
import io.bdeploy.jersey.actions.ActionService;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.jersey.ws.change.ObjectChangeWebSocket;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.messaging.ConnectionHandler;
import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.MessageSender;
import io.bdeploy.messaging.store.imap.custom.ExecuteUnreadMessagesReceiver;
import io.bdeploy.messaging.transport.smtp.SMTPTransportConnectionHandler;
import io.bdeploy.messaging.util.MessagingUtils;
import io.bdeploy.minion.job.CheckLatestGitHubReleaseJob;
import io.bdeploy.minion.job.CleanupDownloadDirJob;
import io.bdeploy.minion.job.MasterCleanupJob;
import io.bdeploy.minion.job.OrganizePoolJob;
import io.bdeploy.minion.job.SyncLdapUserGroupsJob;
import io.bdeploy.minion.migration.SettingsConfigurationMigration;
import io.bdeploy.minion.migration.SystemUserMigration;
import io.bdeploy.minion.nodes.NodeManagerImpl;
import io.bdeploy.minion.plugin.PluginManagerImpl;
import io.bdeploy.minion.user.UserDatabase;
import io.bdeploy.minion.user.UserGroupDatabase;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;
import io.bdeploy.pcu.ProcessController;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.dto.JobDto;
import jakarta.mail.search.SubjectTerm;
import jakarta.ws.rs.WebApplicationException;
import net.jsign.AuthenticodeSigner;
import net.jsign.pe.PEFile;

/**
 * Represents the root directory and configuration of a minion installation.
 */
public class MinionRoot extends LockableDatabase implements Minion, AutoCloseable {

    public static final String MAIL_SUBJECT_PATTERN_CONFIG_OF = "Configuration update of: ";

    private static final String STATE_FILE = "state.json";
    private static final String SESSION_FILE = "ws.json";

    private static final Logger log = LoggerFactory.getLogger(MinionRoot.class);

    private final Path config;
    private final BHive hive;
    private final Path root;
    private final Path hiveDir;
    private final UserDatabase users;
    private final UserGroupDatabase userGroups;
    private final Auditor auditor;
    private final Path logDir;
    private final Path tmpDir;
    private final Path downloadDir;

    private final MinionProcessController processController;
    private final NodeManagerImpl nodeManager;

    private final MessageSender mailSender = new SMTPTransportConnectionHandler();
    private final ExecuteUnreadMessagesReceiver mailReceiver = new ExecuteUnreadMessagesReceiver();

    private Path updates;
    private MinionServerProcessManager serverProcessManager = new MinionServerProcessManager.NoopServerProcessManager();

    private Scheduler scheduler;
    private PluginManager pluginManager;
    private boolean consoleLog;
    private Version latestGitHubReleaseVersion;
    private boolean conCheckFailed = false;
    private SecretKeySpec encryptionKey;

    private ActionService actions;
    private ActionHandle startupAction;

    public MinionRoot(Path root, ActivityReporter reporter) {
        super(root.resolve("etc"));

        root = root.toAbsolutePath().normalize();

        this.root = root;
        this.config = create(root.resolve("etc"));
        this.updates = root.resolve("update");
        this.hiveDir = root.resolve("hive");
        this.hive = new BHive(hiveDir.toUri(), RollingFileAuditor.getFactory().apply(hiveDir), reporter);
        this.userGroups = new UserGroupDatabase(this);
        this.userGroups.addAllUsersGroup();
        this.users = new UserDatabase(this, userGroups);
        this.tmpDir = root.resolve("tmp");

        this.logDir = create(root.resolve("log"));
        this.auditor = RollingFileAuditor.getInstance(logDir);
        this.downloadDir = create(root.resolve("downloads"));

        this.processController = new MinionProcessController();
        this.nodeManager = new NodeManagerImpl();

        this.mailReceiver.addSearchTerm(new SubjectTerm(MAIL_SUBJECT_PATTERN_CONFIG_OF));
    }

    public void configureMessageHandler(Consumer<MessageDataHolder> listener) {
        this.mailReceiver.addOnUnreadMessageFoundListener(listener);
    }

    public void markConnectionCheckFailed() {
        conCheckFailed = true;
    }

    @Override
    public boolean isInitialConnectionCheckFailed() {
        return conCheckFailed;
    }

    @Override
    public boolean isMaster() {
        MinionMode m = getMode();
        return m == MinionMode.CENTRAL || m == MinionMode.MANAGED || m == MinionMode.STANDALONE;
    }

    @Override
    public MinionMode getMode() {
        return getState().mode;
    }

    @Override
    public MinionDto.MinionNodeType getNodeType() {
        return getState().nodeType;
    }

    @Override
    public RemoteService getSelf() {
        return nodeManager.getSelf().remote;
    }

    @Override
    public String getHostName() {
        return getState().officialName;
    }

    @Override
    public Auditor getAuditor() {
        return auditor;
    }

    @Override
    public JerseySessionConfiguration getSessionConfiguration() {
        var sessions = new SessionStorage() {

            @Override
            public void save(Map<String, String> data) {
                MinionSessionState state = new MinionSessionState();
                state.data = data;
                storeConfig(SESSION_FILE, state);
            }

            @Override
            public Map<String, String> load() {
                var dto = readConfig(SESSION_FILE, MinionSessionState.class);
                return dto.data;
            }
        };

        var cfg = getState();

        return JerseySessionConfiguration.withStorage(sessions, cfg.webSessionTimeoutHours, cfg.webSessionActiveTimeoutHours);
    }

    /**
     * @param manager a replacement for the default {@link MinionServerProcessManager}.
     */
    public void setServerProcessManager(MinionServerProcessManager manager) {
        this.serverProcessManager = manager;
    }

    /**
     * @return the {@link MinionServerProcessManager} responsible for restarting this minion.
     */
    public MinionServerProcessManager getServerProcessManager() {
        return serverProcessManager;
    }

    /**
     * Called once when starting the minion root. Can be used for additional
     * initialization
     */
    public void onStartup(boolean consoleLog) {
        this.consoleLog = consoleLog;

        // as early as possible.
        ObjectId baseline = updateLoggingConfiguration(MinionRoot::withBuiltinLogConfig);
        modifyState(s -> s.logConfigId = baseline);

        doMigrate();
        updateMinionConfiguration();

        updateMailHandling();
    }

    /**
     * Called once *after* starting the actual HTTPS server. Can be used for initialization which requires the
     * server to be online already.
     */
    public void afterStartup(boolean isTest, boolean skipAutoStart) {
        // first initialize the node manager.
        nodeManager.initialize(this, isTest);

        // E.g. after applying an update, we do not want to auto-start instances
        // that were stopped manually before the update.
        if (!skipAutoStart) {
            // then start auto-start processes and so on. note: only auto-starts on *this* node.
            // we *must* do it after startup of server to already have a UI where startup
            // could be aborted/monitored/etc.
            processController.autoStart();
        } else {
            log.info("Auto-starting of instances has been skipped.");
        }

        // in case we're in a test, we want to aggressively wait for certain conditions
        // when stopping processes. This is mostly irrelevant in the real world, so save the wait.
        ProcessController.enableWaitForLockRelease(isTest);

        log.info("After startup flow finished.");

        // after all autoStart has completed, the server is finally fully up.
        startupAction.close();
    }

    /**
     * Reads the current {@link SettingsConfiguration} and updates the mail handlers accordingly.
     * <p>
     * <b>Never</b> throws an {@link Exception}.
     */
    public void updateMailHandling() {
        SettingsConfiguration settings;
        try {
            settings = getSettings(false);
        } catch (RuntimeException e) {
            log.error("Failed to retrieve settings.", e);
            return;
        }

        try {
            MailSenderSettingsDto mailSenderSettings = settings.mailSenderSettings;
            updateHandler(mailSender, mailSenderSettings.enabled,//
                    mailSenderSettings.url, mailSenderSettings.username, mailSenderSettings.password);
        } catch (RuntimeException e) {
            log.error("Failed to update mail sender.", e);
        }

        try {
            MailReceiverSettingsDto mailReceiverSettings = settings.mailReceiverSettings;
            updateHandler(mailReceiver, mailReceiverSettings.enabled,//
                    mailReceiverSettings.url, mailReceiverSettings.username, mailReceiverSettings.password);
        } catch (RuntimeException e) {
            log.error("Failed to update mail receiver.", e);
        }
    }

    private static void updateHandler(ConnectionHandler handler, boolean enabled, String url, String user, String password) {
        if (enabled) {
            try {
                handler.connect(MessagingUtils.checkAndParseUrl(url, user, password));
            } catch (IllegalArgumentException e) {
                log.error("URL is invalid.", e);
            }
        } else {
            handler.disconnect();
        }
    }

    /** Updates the logging config file if required, and switches to using it */
    public ObjectId updateLoggingConfiguration(Function<Function<InputStream, ObjectId>, ObjectId> log4jContentSupplier) {
        ObjectId baseline = getState().logConfigId;
        ObjectId current = log4jContentSupplier.apply(ObjectId::createFromStreamNoCopy);

        Path cfgPath = getLoggingConfigurationFile();
        boolean exists = Files.exists(cfgPath);
        if (!exists || baseline == null || !current.equals(baseline)) {
            log.info("Updating logging configuration, lastKnown={}, current={}, exists={}", baseline, current, exists);

            // give a warning if the current version has been locally modified, replace it
            // nevertheless
            if (exists && baseline != null) {
                createLogBackup(baseline, cfgPath);
            }

            // file does not exist, or is outdated - update.
            // record the current ID, so we only copy if the builtin configuration changes.
            baseline = log4jContentSupplier.apply(is -> {
                try {
                    return ObjectId.createByCopy(is, cfgPath);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot update logging configuration", e);
                }
            });
        }

        // in any case, we want to switch to using our copy of the configuration.
        ConfigurationSource source = ConfigurationSource.fromUri(cfgPath.toUri());
        Configuration cfg = ConfigurationFactory.getInstance().getConfiguration(LoggerContext.getContext(false), source);
        Configurator.reconfigure(cfg);

        // set the root's log directory property in the MDC, this is inherited by all
        // threads.
        if (!consoleLog) {
            log.info("Logging into {}", logDir);
            MinionLoggingContextDataProvider.setLogDir(logDir.toAbsolutePath().toString());
        }

        // finally, update the forwarded config for JerseyServer
        JerseyServer.updateLogging();

        return baseline;
    }

    private static void createLogBackup(ObjectId baseline, Path cfgPath) {
        Path backup = null;
        try (InputStream is = Files.newInputStream(cfgPath)) {
            ObjectId local = ObjectId.createFromStreamNoCopy(is);

            if (!local.equals(baseline)) {
                backup = cfgPath.getParent().resolve(cfgPath.getFileName().toString() + ".bak");
                log.warn("Logging configuration has been locally modified - changes will be discarded, backup: {}", backup);
            }
        } catch (IOException e) {
            log.warn("Cannot read existing local logger configuration", e);
        }

        if (backup != null) {
            PathHelper.deleteRecursiveRetry(backup);
            try {
                PathHelper.moveRetry(cfgPath, backup);
            } catch (Exception e) {
                log.warn("Cannot create backup of {} at {}", cfgPath, backup, e);
            }
        }
    }

    public Path getLoggingConfigurationFile() {
        return config.resolve("log4j2.xml");
    }

    private static ObjectId withBuiltinLogConfig(Function<InputStream, ObjectId> function) {
        try (InputStream builtin = MinionRoot.class.getResourceAsStream("/log4j2.xml")) {
            return function.apply(builtin);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot calculate builtin logging config ID", e);
        }
    }

    /**
     * Ensures that the master flag and the version is correctly set in the minion
     * manifest
     */
    private void updateMinionConfiguration() {
        // first we'll want to make sure that minion manifests are in proper shape.
        // they can break in case of power failure and prevent further startup.
        hive.execute(new FsckOperation().setRepair(true)
                .addManifests(hive.execute(new ManifestListOperation().setManifestName(MinionManifest.MANIFEST_NAME))));

        // next it must be save to read the manifest.
        MinionManifest manifest = new MinionManifest(hive);
        MinionConfiguration minionConfig = manifest.read();

        if (minionConfig == null) {
            throw new IllegalStateException("Minion Configuration has been corrupted.");
        }

        // Check that the master flag is set on the correct entry
        boolean writeManifest = false;
        for (Map.Entry<String, MinionDto> entry : minionConfig.entrySet()) {
            if (doUpdateMinionConfiguration(entry.getKey(), entry.getValue())) {
                writeManifest = true;
            }
        }

        // Avoid writing new versions if nothing changed
        if (writeManifest) {
            manifest.update(minionConfig);
        }
    }

    /**
     * Synchronizes the given configuration with the real values provided by the
     * minion.
     *
     * @param minionName name of the minion
     * @param minionDto current minion configuration
     * @return {@code true} if the configuration changed
     */
    private boolean doUpdateMinionConfiguration(String minionName, MinionDto minionDto) {
        boolean isMaster = isMaster();
        String myName = getState().self;

        // Ensure that the master flag is set correctly
        boolean changed = false;
        if (minionName.equals(myName) && isMaster && !minionDto.master) {
            minionDto.master = true;
            changed = true;
        } else if (minionDto.master && !isMaster) {
            minionDto.master = false;
            changed = true;
        }

        // Update our own version
        if (myName.equals(minionName)) {
            Version running = VersionHelper.getVersion();
            if (!VersionHelper.equals(running, minionDto.version)) {
                minionDto.version = running;
                changed = true;
            }
            return changed;
        }

        return changed;
    }

    /**
     * Does whatever is required to migrate an older version to the current version
     */
    private void doMigrate() {
        if (VersionHelper.isRunningUndefined()) {
            log.debug("Skipping migration as the running version is undefined.");
            return;
        }

        Version current = VersionHelper.getVersion();
        String lastMigrated = getState().fullyMigratedVersion;
        if (lastMigrated != null && lastMigrated.equals(current.toString())) {
            // already performed migration, skip
            log.debug("Already fully migrated to {}", lastMigrated);
            return;
        }

        try {
            // first step is to backup state.json (long-term backup) in case something is
            // fishy during migration, or even later on.
            Path cfgPath = config.resolve(STATE_FILE);
            Path cfgBakPath = config.resolve(STATE_FILE + ".pre-mig-bak");
            if (Files.exists(cfgPath)) {
                Files.copy(cfgPath, cfgBakPath, StandardCopyOption.REPLACE_EXISTING);
            }

            SystemUserMigration.run(this);
            SettingsConfigurationMigration.run(this);
        } catch (Exception e) {
            throw new IllegalStateException("Minion update migration failed", e);
        }

        // if all migrations succeeded (did not throw), record version
        modifyState(s -> s.fullyMigratedVersion = current.toString());
    }

    /**
     * Returns the job scheduler
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    @Override
    public List<JobDto> listJobs() {
        try {
            Set<JobKey> currentJobs = scheduler.getCurrentlyExecutingJobs().stream().map(j -> j.getJobDetail().getKey())
                    .collect(Collectors.toSet());
            List<JobDto> result = new ArrayList<>();
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.anyGroup())) {
                JobDetail detail = scheduler.getJobDetail(jobKey);
                JobDto dto = new JobDto();
                dto.name = jobKey.getName();
                dto.description = detail.getDescription();
                dto.group = jobKey.getGroup();
                dto.isRunning = currentJobs.contains(jobKey);
                Optional.ofNullable(scheduler.getTriggersOfJob(jobKey)).map(List::getLast).map(Trigger::getNextFireTime)
                        .ifPresent(nextFireTime -> dto.nextRunTime = nextFireTime.getTime());
                getLastRunTime(jobKey).ifPresent(time -> dto.lastRunTime = time);
                result.add(dto);
            }
            return result;
        } catch (SchedulerException e) {
            log.warn("Failed to list jobs");
            if (log.isTraceEnabled()) {
                log.trace("SchedulerException", e);
            }
            throw new IllegalStateException("Failed to list jobs", e);
        }
    }

    private Optional<Long> getLastRunTime(JobKey jobKey) {
        if (jobKey.equals(SyncLdapUserGroupsJob.JOB_KEY)) {
            return Optional.ofNullable(getState().ldapSyncLastRun);
        } else if (jobKey.equals(CheckLatestGitHubReleaseJob.JOB_KEY)) {
            return Optional.ofNullable(getState().checkLatestGitHubReleaseLastRun);
        } else if (jobKey.equals(MasterCleanupJob.JOB_KEY)) {
            return Optional.ofNullable(getState().cleanupLastRun);
        } else if (jobKey.equals(CleanupDownloadDirJob.JOB_KEY)) {
            return Optional.ofNullable(getState().cleanupDownloadsDirLastRun);
        } else if (jobKey.equals(OrganizePoolJob.JOB_KEY)) {
            return Optional.ofNullable(getState().poolOrganizationLastRun);
        }
        return Optional.empty();
    }

    @Override
    public void runJob(JobDto jobDto) {
        try {
            JobKey jobKey = new JobKey(jobDto.name, jobDto.group);
            scheduler.triggerJob(jobKey);
        } catch (SchedulerException e) {
            log.warn("Failed to trigger job {} immediately", jobDto.name);
            if (log.isTraceEnabled()) {
                log.trace("SchedulerException", e);
            }
            throw new IllegalStateException("Failed to trigger job", e);
        }
    }

    @Override
    public NodeManager getNodeManager() {
        return nodeManager;
    }

    @Override
    public MinionDto getSelfConfig() {
        return nodeManager.getSelf();
    }

    @Override
    public Path getDownloadDir() {
        return downloadDir;
    }

    @Override
    public Path getTempDir() {
        return tmpDir;
    }

    public MessageSender getMailSender() {
        return mailSender;
    }

    /**
     * Setup tasks which should only run when this root is used for serving a
     * minion.
     */
    public void setupServerTasks(MinionMode minionMode, BHiveRegistry registry) {
        // cleanup any stale things so periodic tasks don't get them wrong.
        PathHelper.deleteRecursiveRetry(getTempDir());
        PathHelper.mkdirs(getTempDir());

        if (minionMode != MinionMode.CENTRAL) {
            initProcessController();
        }

        createJobScheduler();

        if (minionMode != MinionMode.NODE) {
            MasterCleanupJob.create(this, getState().cleanupSchedule);
        }
        CleanupDownloadDirJob.create(scheduler, downloadDir, this);

        if (minionMode == MinionMode.STANDALONE) {
            CheckLatestGitHubReleaseJob.create(this);
        }

        SyncLdapUserGroupsJob.create(this, getState().ldapSyncSchedule);
        OrganizePoolJob.create(this, registry, getState().poolOrganizationSchedule);
    }

    private void createJobScheduler() {
        try {
            scheduler = DirectSchedulerFactory.getInstance().getScheduler(root.toString());

            // might have been initialized before, e.g. tests in the same VM.
            if (scheduler == null) {
                SimpleThreadPool tp = new SimpleThreadPool(2, Thread.NORM_PRIORITY);
                tp.setThreadNamePrefix("MinionScheduler");

                DirectSchedulerFactory.getInstance().createScheduler(root.toString(), "MinionScheduler", tp, new RAMJobStore());
                scheduler = DirectSchedulerFactory.getInstance().getScheduler(root.toString());
                scheduler.start();
            } else {
                log.info("Re-using existing Scheduler {}", scheduler);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot create job scheduler", e);
        }
    }

    @Override
    public void close() {
        hive.close();
        auditor.close();
        nodeManager.close();
        mailSender.close();
        mailReceiver.close();

        if (scheduler != null) {
            try {
                scheduler.shutdown();
            } catch (SchedulerException e) {
                log.error("Cannot shutdown job scheduler", e);
            }
        }
    }

    /**
     * Initialize keys if they are not yet there.
     */
    public MinionState initKeys() {
        MinionState state = new MinionState();

        if (state.keystorePath == null || !Files.exists(state.keystorePath)) {
            Path ks = config.resolve("private");
            state.keystorePath = ks;
            state.keystorePass = Base64
                    .encodeBase64String(Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)).toCharArray();

            try {
                // generate certificate and keystore.
                BCX509Helper.createKeyStore(ks, state.keystorePass);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot initialize keys for minion", e);
            }
        }

        return state;
    }

    public void initHttpKeys() {
        modifyState(state -> {
            if (state.keystoreHttpsPath != null && PathHelper.exists(state.keystoreHttpsPath)) {
                throw new IllegalStateException("HTTPS keystore already initialized");
            }

            Path ks = config.resolve("private.https");
            state.keystoreHttpsPath = ks;

            try {
                BCX509Helper.createEmptyKeystore(ks, state.keystorePass);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot initialize HTTPS keys for minion", e);
            }
        });
    }

    public synchronized SecretKeySpec getEncryptionKey() {
        if (encryptionKey == null) {
            encryptionKey = createEncryptionKey();
        }
        return encryptionKey;
    }

    private SecretKeySpec createEncryptionKey() {
        try {
            return SecurityHelper.createSecretKey(getState().keystorePass);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot create encryption key", e);
        }
    }

    public MinionState getState() {
        AtomicReference<MinionState> ref = new AtomicReference<>(null);
        locked(() -> {
            MinionState s = readConfig(STATE_FILE, MinionState.class);
            if (s.keystorePath == null) {
                throw new IllegalStateException("Minion root not initialized!");
            }
            ref.set(s);
        });
        return ref.get();
    }

    public void setState(MinionState s) {
        locked(() -> storeConfig(STATE_FILE, s));
    }

    public void modifyState(Consumer<MinionState> modifier) {
        MinionState s = getState();
        modifier.accept(s);
        setState(s);
    }

    public Path getDeploymentDir() {
        Path dir = getState().deploymentDir;
        if (dir == null) {
            dir = root.resolve("deploy");
        }
        return create(dir).normalize();
    }

    public Path getLogDataDir() {
        Path dir = getState().logDataDir;
        if (dir == null) {
            return null;
        }
        return create(dir).normalize();
    }

    public Path getLogDir() {
        return logDir;
    }

    public Path getRootDir() {
        return root;
    }

    /** Creates the given directory */
    private static Path create(Path dir) {
        PathHelper.mkdirs(dir);
        return dir;
    }

    public Path getUpdateDir() {
        return create(updates);
    }

    public void setUpdateDir(Path updateDir) {
        updates = updateDir;
    }

    public List<Path> getStorageLocations() {
        List<Path> paths = getState().storageLocations;
        if (paths == null || paths.isEmpty()) {
            Path defPath = root.resolve("storage").toAbsolutePath();
            PathHelper.mkdirs(defPath);
            List<Path> p = new ArrayList<>();
            p.add(defPath);
            modifyState(s -> s.storageLocations = p);
            return p;
        }
        return paths.stream().map(Path::normalize).toList();
    }

    private <T> T readConfig(String name, Class<T> clazz) {
        Path cfg = config.resolve(name);
        Path bakCfg = config.resolve(name + ".bak");

        if (!Files.exists(cfg) && Files.exists(bakCfg)) {
            log.warn("state.json is missing, restoring from state.json.bak");
            try {
                PathHelper.moveRetry(bakCfg, cfg, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                log.error("Cannot automatically restore {} from {}", cfg, bakCfg, e);
            }
        }

        if (!Files.exists(cfg)) {
            try {
                return clazz.getConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create configuration " + name, e);
            }
        }

        try (InputStream is = Files.newInputStream(cfg)) {
            return StorageHelper.fromStream(is, clazz);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load minion config " + name, e);
        }
    }

    private void storeConfig(String name, Object cfg) {
        Path cfgPath = config.resolve(name);
        Path cfgTmpPath = config.resolve(name + ".tmp");
        Path cfgBakPath = config.resolve(name + ".bak");

        try {
            Files.write(cfgTmpPath, StorageHelper.toRawBytes(cfg), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
            if (Files.exists(cfgPath)) {
                PathHelper.moveRetry(cfgPath, cfgBakPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            PathHelper.moveRetry(cfgTmpPath, cfgPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot save minion config " + name, e);
        }
    }

    /**
     * @return The internal minion hive. This hive is used to hold anything which is
     *         deployed/deployable on the node. The master uses named hives (per
     *         instance group) to store higher level (node-spanning) deployment
     *         information.
     */
    public BHive getHive() {
        return hive;
    }

    public Path getHiveDir() {
        return hiveDir;
    }

    /**
     * Returns the process controller of this minion.
     */
    public MinionProcessController getProcessController() {
        return processController;
    }

    /**
     * @return the {@link UserDatabase} used for simple (local) authentication of
     *         users.
     */
    public UserDatabase getUsers() {
        return users;
    }

    /**
     * @return the {@link UserGroupDatabase} used for holding permissions for groups of users
     */
    public UserGroupDatabase getUserGroups() {
        return userGroups;
    }

    /**
     * Determines what is currently deployed and passes this information to the
     * process controller.
     */
    private void initProcessController() {
        log.info("Initializing process controller.");

        // Find all deployed instance node controllers and add them to the controller
        SortedSet<Key> keys = InstanceNodeManifest.scan(hive);
        if (keys.isEmpty()) {
            log.info("No instances are currently deployed on any node.");
            return;
        }
        SortedMap<String, Manifest.Key> activeVersions = new TreeMap<>();
        for (Key k : keys) {
            initProcessControllerForInstance(activeVersions, k);
        }

        // Check what is running and launch applications
        processController.recover();

        // Startup processes according to their configuration
        processController.setActiveVersions(activeVersions);

        // don't perform actual start here - we delay this until #afterStartup
    }

    private void initProcessControllerForInstance(SortedMap<String, Manifest.Key> activeVersions, Key key) {
        try {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
            InstanceNodeController inc = new InstanceNodeController(hive, getDeploymentPaths(inm), inm,
                    new TaskSynchronizer());
            if (!inc.isInstalled()) {
                return;
            }

            // Get the deployment configuration and the target directory
            String tag = inm.getKey().getTag();
            ProcessGroupConfiguration pgc = inc.getProcessGroupConfiguration();
            if (pgc == null) {
                String instanceId = inm.getConfiguration().id;
                log.warn("{} / {} - Cannot read persisted process configuration.", instanceId, tag);
                return;
            }

            // Create controller and add to the affected instance
            InstanceProcessController instanceController = processController.getOrCreate(inm);
            instanceController.createProcessControllers(inc.getDeploymentPathProvider(), inc.getResolver(), inm, tag, pgc,
                    inm.getRuntimeHistory(hive));

            // fetch and remember the active version for this id.
            if (!activeVersions.containsKey(inm.getId())) {
                String active = inm.getState(hive).read().activeTag;
                if (active != null) {
                    activeVersions.put(inm.getId(), new Manifest.Key(inm.getKey().getName(), active));
                }
            }
        } catch (Exception e) {
            log.error("Cannot setup process control for {}", key, e);
        }
    }

    /**
     * Checks whether a given {@link Path} is under control of this
     * {@link MinionRoot}, including managed directories, storage locations, etc.
     *
     * @param toCheck a {@link Path} to check
     * @return whether the given {@link Path} "belongs" to the minion.
     */
    public boolean isManagedPath(Path toCheck) {
        if (toCheck.normalize().startsWith(root)) {
            return true;
        }

        for (Path loc : getStorageLocations()) {
            if (toCheck.startsWith(loc)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String createWeakToken(String principal) {
        ApiAccessToken token = new ApiAccessToken.Builder().setIssuedTo(principal).setWeak(true).build();
        MinionState state = getState();
        try {
            KeyStore ks = SecurityHelper.getInstance().loadPrivateKeyStore(state.keystorePath, state.keystorePass);
            return SecurityHelper.getInstance().createSignaturePack(token, ks, state.keystorePass);
        } catch (GeneralSecurityException | IOException e) {
            throw new WebApplicationException("Cannot generate weak token", e);
        }
    }

    @Override
    public String createToken(String principal, Collection<ScopedPermission> permissions, boolean full) {
        ApiAccessToken token = new ApiAccessToken.Builder().setIssuedTo(principal).setWeak(false).addPermission(permissions)
                .build();
        MinionState state = getState();
        try {
            KeyStore ks = SecurityHelper.getInstance().loadPrivateKeyStore(state.keystorePath, state.keystorePass);
            if (full) {
                return SecurityHelper.getInstance().createSignaturePack(token, ks, state.keystorePass);
            } else {
                return SecurityHelper.getInstance().createToken(token, ks, state.keystorePass);
            }
        } catch (GeneralSecurityException | IOException e) {
            throw new WebApplicationException("Cannot generate user token", e);
        }
    }

    @Override
    public <T> String getEncryptedPayload(T payload) {
        MinionState state = getState();

        try {
            KeyStore ks = SecurityHelper.getInstance().loadPrivateKeyStore(state.keystorePath, state.keystorePass);
            return SecurityHelper.getInstance().createSignaturePack(payload, ks, state.keystorePass);
        } catch (GeneralSecurityException | IOException e) {
            throw new WebApplicationException("Cannot create encrypted payload", e);
        }

    }

    @Override
    public <T> T getDecryptedPayload(String encrypted, Class<T> clazz) {
        try {
            return SecurityHelper.getInstance().getSelfVerifiedPayloadFromPack(encrypted, clazz);
        } catch (GeneralSecurityException | IOException e) {
            throw new WebApplicationException("Cannot decrypt payload", e);
        }
    }

    public <T> T getDecryptedPayload(String enctrypted, Class<T> clazz, Certificate cert) {
        try {
            return SecurityHelper.getInstance().getVerifiedPayloadFromPack(enctrypted, clazz, cert);
        } catch (GeneralSecurityException e) {
            throw new WebApplicationException("Cannot decrypt payload", e);
        }
    }

    public Certificate getCertificateOfRemote(String remoteAuth) {
        if (remoteAuth == null) {
            throw new IllegalArgumentException("RemoteService does not carry authentication information");
        }

        try {
            return SecurityHelper.getInstance().getCertificateFromToken(remoteAuth);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot extract certificate from token", e);
        }
    }

    @Override
    public void signExecutable(File executable, String name, String host) {
        MinionState state = getState();
        try {
            KeyStore keystore = SecurityHelper.getInstance().loadPrivateKeyStore(state.keystorePath, state.keystorePass);
            AuthenticodeSigner signer = new AuthenticodeSigner(keystore, keystore.aliases().nextElement(),
                    new String(state.keystorePass));
            signer.withProgramName(name).withProgramURL(host).withTimestamping(false);
            signer.sign(new PEFile(executable));
        } catch (Exception e) {
            throw new WebApplicationException("Cannot sign executable", e);
        }
    }

    public PluginManager createPluginManager(JerseyServer srv) {
        if (pluginManager != null) {
            return pluginManager;
        }
        pluginManager = new PluginManagerImpl(getHive(), srv);
        return pluginManager;
    }

    @Override
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public SettingsConfiguration getSettings(boolean clearPasswords) {
        SettingsConfiguration s = SettingsManifest.read(hive, getEncryptionKey(), clearPasswords);

        if (s.auth.oidcSettings == null) {
            s.auth.oidcSettings = new OIDCSettingsDto();
        }

        if (s.auth.auth0Settings == null) {
            s.auth.auth0Settings = new Auth0SettingsDto();
        }

        if (s.auth.oktaSettings == null) {
            s.auth.oktaSettings = new OktaSettingsDto();
        }

        if (s.mailSenderSettings == null) {
            s.mailSenderSettings = new MailSenderSettingsDto();
        }

        if (s.mailSenderSettings == null) {
            s.mailReceiverSettings = new MailReceiverSettingsDto();
        }

        return s;
    }

    @Override
    public void setSettings(SettingsConfiguration settings) {
        SettingsManifest.write(hive, settings, getEncryptionKey());
        updateMailHandling();
    }

    public void setLatestGitHubReleaseVersion(Version v) {
        this.latestGitHubReleaseVersion = v;
    }

    @Override
    public boolean isNewGitHubReleaseAvailable() {
        Version currentVersion = VersionHelper.getVersion();
        return currentVersion != null && this.latestGitHubReleaseVersion != null
                && this.latestGitHubReleaseVersion.compareTo(currentVersion) > 0;
    }

    public ActionService createActionService(ObjectChangeWebSocket ocws) {
        actions = new ActionService(ocws, auditor);
        startupAction = actions.start(new Action(Actions.STARTING_SERVER, null, null, null), ActionExecution.fromSystem());
        return actions;
    }

    public ActionService getActions() {
        return actions;
    }

    @Override
    public Path getDefaultPoolPath() {
        return getState().poolDefaultPath;
    }

    @Override
    public DeploymentPathProvider getDeploymentPaths(InstanceNodeManifest inm) {
        return new DeploymentPathProvider(getDeploymentDir(), getLogDataDir(), inm);
    }
}
