package io.bdeploy.minion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.simpl.RAMJobStore;
import org.quartz.simpl.SimpleThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.LockableDatabase;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.Version;
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
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.jersey.audit.Auditor;
import io.bdeploy.jersey.audit.RollingFileAuditor;
import io.bdeploy.minion.job.CleanupDownloadDirJob;
import io.bdeploy.minion.job.MasterCleanupJob;
import io.bdeploy.minion.job.NodeMonitorJob;
import io.bdeploy.minion.migration.MinionStateMigration;
import io.bdeploy.minion.migration.SettingsConfigurationMigration;
import io.bdeploy.minion.migration.SystemUserMigration;
import io.bdeploy.minion.migration.UpdatePackagingMigration;
import io.bdeploy.minion.plugin.PluginManagerImpl;
import io.bdeploy.minion.user.UserDatabase;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import jakarta.ws.rs.WebApplicationException;
import net.jsign.AuthenticodeSigner;
import net.jsign.pe.PEFile;

/**
 * Represents the root directory and configuration of a minion installation.
 */
public class MinionRoot extends LockableDatabase implements Minion, AutoCloseable {

    private static final String STATE_FILE = "state.json";

    private static final Logger log = LoggerFactory.getLogger(MinionRoot.class);

    private final Path config;
    private final BHive hive;
    private final Path root;
    private final Path hiveDir;
    private final UserDatabase users;
    private final Auditor auditor;
    private final Path logDir;
    private final Path tmpDir;
    private final Path downloadDir;

    private final MinionProcessController processController;

    private Path updates;
    private MinionUpdateManager updateManager = t -> log.error("No Update Manager, cannot update Minion!");

    private Scheduler scheduler;
    private PluginManager pluginManager;
    private boolean consoleLog;

    public MinionRoot(Path root, ActivityReporter reporter) {
        super(root.resolve("etc"));

        root = root.toAbsolutePath();

        this.root = root;
        this.config = create(root.resolve("etc"));
        this.updates = root.resolve("update");
        this.hiveDir = root.resolve("hive");
        this.hive = new BHive(hiveDir.toUri(), reporter);
        this.users = new UserDatabase(this);
        this.tmpDir = root.resolve("tmp");

        this.logDir = create(root.resolve("log"));
        this.auditor = new RollingFileAuditor(logDir);
        this.downloadDir = create(root.resolve("downloads"));

        this.processController = new MinionProcessController();
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
    public RemoteService getSelf() {
        MinionState state = getState();
        return getMinions().getRemote(state.self);
    }

    @Override
    public String getHostName() {
        return getState().officialName;
    }

    @Override
    public Auditor getAuditor() {
        return auditor;
    }

    /**
     * @param manager a replacement for the default {@link MinionUpdateManager}.
     */
    public void setUpdateManager(MinionUpdateManager manager) {
        this.updateManager = manager;
    }

    /**
     * @return the {@link MinionUpdateManager} responsible for updating this minion.
     */
    public MinionUpdateManager getUpdateManager() {
        return updateManager;
    }

    /**
     * Called once when starting the minion root. Can be used for additional initialization
     */
    public void onStartup(boolean consoleLog) {
        this.consoleLog = consoleLog;

        // as early as possible.
        ObjectId baseline = updateLoggingConfiguration(this::withBuiltinLogConfig);
        modifyState(s -> s.logConfigId = baseline);

        doMigrate();
        updateMinionConfiguration();
    }

    /** Updates the logging config file if required, and switches to using it */
    public ObjectId updateLoggingConfiguration(Function<Function<InputStream, ObjectId>, ObjectId> log4jContentSupplier) {
        ObjectId baseline = getState().logConfigId;
        ObjectId current = log4jContentSupplier.apply(ObjectId::createFromStreamNoCopy);

        Path cfgPath = getLoggingConfigurationFile();
        boolean exists = Files.exists(cfgPath);
        if (!exists || baseline == null || !current.equals(baseline)) {
            log.info("Updating logging configuration, lastKnown={}, current={}, exists={}", baseline, current, exists);

            // give a warning if the current version has been locally modified, replace it nevertheless
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

        // set the root's log directory property in the MDC, this is inherited by all threads.
        if (!consoleLog) {
            log.info("Logging into {}", logDir);
            MinionLoggingContextDataProvider.setLogDir(logDir.toAbsolutePath().toString());
        }

        return baseline;
    }

    private void createLogBackup(ObjectId baseline, Path cfgPath) {
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
            PathHelper.deleteRecursive(backup);
            try {
                Files.move(cfgPath, backup);
            } catch (IOException e) {
                log.warn("Cannot create backup of {} at {}", cfgPath, backup, e);
            }
        }
    }

    public Path getLoggingConfigurationFile() {
        return config.resolve("log4j2.xml");
    }

    private ObjectId withBuiltinLogConfig(Function<InputStream, ObjectId> function) {
        try (InputStream builtin = MinionRoot.class.getResourceAsStream("/log4j2.xml")) {
            return function.apply(builtin);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot calculate builtin logging config ID", e);
        }
    }

    /** Ensures that the master flag and the version is correctly set in the minion manifest */
    private void updateMinionConfiguration() {
        MinionManifest manifest = new MinionManifest(hive);
        MinionConfiguration minionConfig = manifest.read();

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
     * Synchronizes the given configuration with the real values provided by the minion.
     *
     * @param minionName
     *            name of the minion
     * @param config
     *            current minion configuration
     * @return {@code true} if the configuration changed
     */
    private boolean doUpdateMinionConfiguration(String minionName, MinionDto config) {
        boolean isMaster = isMaster();
        String myName = getState().self;

        // Ensure that the master flag is set correctly
        boolean changed = false;
        if (minionName.equals(myName) && isMaster && !config.master) {
            config.master = true;
            changed = true;
        } else if (config.master && !isMaster) {
            config.master = false;
            changed = true;
        }

        // Update our own version
        if (myName.equals(minionName)) {
            Version running = VersionHelper.getVersion();
            if (!VersionHelper.equals(running, config.version)) {
                config.version = running;
                changed = true;
            }
            return changed;
        }

        // Contact minion to get the current version
        MinionStatusDto remoteStatus = MinionHelper.tryContactMinion(config.remote, 1, 0);
        if (remoteStatus == null) {
            log.warn("Configured minion '{}' is currently offline.", minionName);
            return changed;
        }
        MinionDto remoteConfig = remoteStatus.config;
        log.info("Minion '{}' successfully contacted. Version={} OS={}", minionName, remoteConfig.version, remoteConfig.os);

        // Check if an update is required
        if (!VersionHelper.equals(config.version, remoteConfig.version)) {
            config.version = remoteConfig.version;
            changed = true;
        }
        return changed;
    }

    /** Does whatever is required to migrate an older version to the current version */
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
            // first step is to backup state.json (long-term backup) in case something is fishy during migration, or even later on.
            Path cfgPath = config.resolve(STATE_FILE);
            Path cfgBakPath = config.resolve(STATE_FILE + ".pre-mig-bak");
            if (Files.exists(cfgPath)) {
                Files.copy(cfgPath, cfgBakPath, StandardCopyOption.REPLACE_EXISTING);
            }

            UpdatePackagingMigration.run(this);
            MinionStateMigration.run(this);
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
    public MinionDto getMinionConfig() {
        String myName = getState().self;
        return getMinions().getMinion(myName);
    }

    @Override
    public MinionConfiguration getMinions() {
        return MinionManifest.getConfiguration(hive);
    }

    @Override
    public Path getDownloadDir() {
        return downloadDir;
    }

    @Override
    public Path getTempDir() {
        return tmpDir;
    }

    /**
     * Setup tasks which should only run when this root is used for serving a
     * minion.
     */
    public void setupServerTasks(MinionMode minionMode) {
        // cleanup any stale things so periodic tasks don't get them wrong.
        PathHelper.deleteRecursive(getTempDir());
        PathHelper.mkdirs(getTempDir());

        if (minionMode != MinionMode.CENTRAL) {
            initProcessController();
        }

        createJobScheduler();

        if (minionMode != MinionMode.NODE) {
            MasterCleanupJob.create(this, getState().cleanupSchedule);
        }
        CleanupDownloadDirJob.create(scheduler, downloadDir);
        NodeMonitorJob.create(this);
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

    public SecretKeySpec getEncryptionKey() {
        try {
            return SecurityHelper.createSecretKey(getState().keystorePass);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot create encryption key", e);
        }
    }

    /**
     * Read the minions state.json with a non-default class. This can be used to read the state.json file
     * with a custom class for state migration, which contains fields that are no longer in state.json.
     */
    public <T> T getPartialStateForMigration(Class<T> clazz) {
        return readConfig(STATE_FILE, clazz);
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
        return create(dir);
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
        return paths;
    }

    private <T> T readConfig(String name, Class<T> clazz) {
        Path cfg = config.resolve(name);

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
            Files.write(cfgTmpPath, StorageHelper.toRawBytes(cfg));
            if (Files.exists(cfgPath)) {
                Files.move(cfgPath, cfgBakPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(cfgTmpPath, cfgPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save minion config " + name, e);
        }
    }

    /**
     * @return The internal minion hive. This hive is used to hold anything which is deployed/deployable on the node. The master
     *         uses named hives (per instance group) to store higher level (node-spanning) deployment information.
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
     * @return the {@link UserDatabase} used for simple (local) authentication of users.
     */
    public UserDatabase getUsers() {
        return users;
    }

    /**
     * Determines what is currently deployed and passes this information to the process controller.
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
        for (Key key : keys) {
            initProcessControllerForInstance(activeVersions, key);
        }

        // Check what is running and launch applications
        processController.recover();

        // Startup processes according to their configuration
        processController.setActiveVersions(activeVersions);
        processController.autoStart();
    }

    private void initProcessControllerForInstance(SortedMap<String, Manifest.Key> activeVersions, Key key) {
        try {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
            InstanceNodeController inc = new InstanceNodeController(hive, getDeploymentDir(), inm);
            if (!inc.isInstalled()) {
                return;
            }

            // Get the deployment configuration and the target directory
            String tag = inm.getKey().getTag();
            ProcessGroupConfiguration pgc = inc.getProcessGroupConfiguration();
            if (pgc == null) {
                String instanceId = inm.getConfiguration().uuid;
                log.warn("{} / {} - Cannot read persisted process configuration.", instanceId, tag);
                return;
            }

            // Create controller and add to the affected instance
            InstanceProcessController instanceController = processController.getOrCreate(hive, inm);
            instanceController.createProcessControllers(inc.getDeploymentPathProvider(), inc.getResolver(), tag, pgc,
                    inm.getRuntimeHistory(hive));

            // fetch and remember the active version for this uuid.
            if (!activeVersions.containsKey(inm.getUUID())) {
                String active = inm.getState(hive).read().activeTag;
                if (active != null) {
                    activeVersions.put(inm.getUUID(), new Manifest.Key(inm.getKey().getName(), active));
                }
            }
        } catch (Exception e) {
            log.error("Cannot setup process control for {}", key, e);
        }
    }

    /**
     * Checks whether a given {@link Path} is under control of this {@link MinionRoot}, including managed directories, storage
     * locations, etc.
     *
     * @param toCheck a {@link Path} to check
     * @return whether the given {@link Path} "belongs" to the minion.
     */
    public boolean isManagedPath(Path toCheck) {
        if (toCheck.startsWith(root)) {
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
    public SettingsConfiguration getSettings() {
        return SettingsManifest.read(hive, getEncryptionKey(), true);
    }

    @Override
    public void setSettings(SettingsConfiguration settings) {
        SettingsManifest.write(hive, settings, getEncryptionKey());
    }

}
