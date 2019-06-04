package io.bdeploy.minion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.codec.binary.Base64;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.simpl.RAMJobStore;
import org.quartz.simpl.SimpleThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.objects.LockableDatabase;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.jersey.audit.Auditor;
import io.bdeploy.jersey.audit.RollingFileAuditor;
import io.bdeploy.minion.cleanup.MasterCleanupJob;
import io.bdeploy.minion.tasks.CleanupDownloadDirTask;
import io.bdeploy.minion.user.UserDatabase;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;
import io.bdeploy.ui.api.Minion;

/**
 * Represents the root directory and configuration of a minion installation.
 */
public class MinionRoot extends LockableDatabase implements Minion, AutoCloseable {

    /** Default schedule for cleanup job - once a day at 2:00am. */
    public static final String DEFAULT_CLEANUP_SCHEDULE = "0 0 2 * * ?";

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
    private CleanupDownloadDirTask cleanupDownloadDirTask;

    private Path updates;
    private MinionUpdateManager updateManager = (t) -> log.error("No Update Manager, cannot update Minion!");

    private Scheduler scheduler;

    public MinionRoot(Path root, ActivityReporter reporter) {
        super(root.resolve("etc"));

        this.root = root;
        this.config = create(root.resolve("etc"));
        this.updates = root.resolve("update");
        this.hiveDir = root.resolve("hive");
        this.hive = new BHive(hiveDir.toUri(), reporter);
        this.users = new UserDatabase(this.hive);
        this.tmpDir = root.resolve("tmp");

        this.logDir = create(root.resolve("log"));
        this.auditor = new RollingFileAuditor(logDir);
        this.downloadDir = create(root.resolve("downloads"));

        this.processController = new MinionProcessController();
    }

    /**
     * @return the {@link Auditor} responsible for this {@link MinionRoot}.
     */
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

    @Override
    public Path getDownloadDir() {
        return downloadDir;
    }

    @Override
    public Path getTempDir() {
        return tmpDir;
    }

    private void cleanup() {
        PathHelper.deleteRecursive(getTempDir());
        PathHelper.mkdirs(getTempDir());
    }

    /**
     * Setup tasks which should only run when this root is used for serving a
     * minion.
     */
    public void setupServerTasks(boolean master) {
        // cleanup any stale things so periodic tasks don't get them wrong.
        cleanup();

        this.cleanupDownloadDirTask = new CleanupDownloadDirTask(downloadDir);
        this.cleanupDownloadDirTask.start();

        initProcessController();
        createJobScheduler();

        if (master) {
            String cronSchedule = getState().cleanupSchedule;
            if (cronSchedule == null) {
                cronSchedule = DEFAULT_CLEANUP_SCHEDULE;
            }

            initCleanupJob(cronSchedule);
        }
    }

    /**
     * Initializes (or updates) the cleanup job to the given schedule.
     *
     * @param cronSchedule the schedule for the cleanup job.
     */
    public void initCleanupJob(String cronSchedule) {
        JobDetail cleanupJob = JobBuilder.newJob(MasterCleanupJob.class).withDescription("Master Cleanup Job")
                .withIdentity("Cleanup", "Master")
                .usingJobData(new JobDataMap(Collections.singletonMap(MasterCleanupJob.DATA_ROOT, this))).build();
        Trigger trigger = createCronTrigger(cleanupJob.getKey(), cronSchedule);

        try {
            if (scheduler.checkExists(trigger.getKey())) {
                log.info("Re-scheduling cleanup job to '" + cronSchedule + "'");
                scheduler.rescheduleJob(trigger.getKey(), trigger);
            } else {
                scheduler.scheduleJob(cleanupJob, trigger);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot schedule cleanup job", e);
        }
    }

    private Trigger createCronTrigger(JobKey job, String cronSchedule) {
        Trigger trigger;
        try {
            trigger = TriggerBuilder.newTrigger().forJob(job).withIdentity("CleanupTrigger", "Master").startNow()
                    .usingJobData(MasterCleanupJob.SCHEDULE, cronSchedule)
                    .withSchedule(CronScheduleBuilder.cronScheduleNonvalidatedExpression(cronSchedule)).build();
        } catch (ParseException e) {
            log.error("Invalid cron schedule: {} using default instead", cronSchedule);
            trigger = TriggerBuilder.newTrigger().forJob(job).startNow()
                    .withSchedule(CronScheduleBuilder.cronSchedule(DEFAULT_CLEANUP_SCHEDULE)).build();
        }

        return trigger;
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

    public MinionState getState() {
        AtomicReference<MinionState> ref = new AtomicReference<>(null);
        locked(() -> {
            MinionState s = readConfig("state.json", MinionState.class);
            if (s.keystorePath == null) {
                throw new IllegalStateException("Minion root not initialized!");
            }
            ref.set(s);
        });
        return ref.get();
    }

    public void setState(MinionState s) {
        locked(() -> storeConfig("state.json", s));
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

    public Path getAuditLogDir() {
        return logDir;
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
            modifyState((s) -> {
                s.storageLocations = p;
            });
            return p;
        }
        return paths;
    }

    public <T> T readConfig(String name, Class<T> clazz) {
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

    public void storeConfig(String name, Object cfg) {
        Path cfgPath = config.resolve(name);

        try {
            Files.write(cfgPath, StorageHelper.toRawBytes(cfg));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save minion config " + name, e);
        }
    }

    /**
     * @return The internal minion hive. This hive is used to hold anything which is deployed/deployable on the minion (slave
     *         side). The master uses named hives (per customer) to store higher level (minion-spanning) deployment information.
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
        for (Key key : keys) {
            Path deploymentDir = getDeploymentDir();

            try {
                InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
                InstanceNodeController inc = new InstanceNodeController(hive, deploymentDir, inm);
                if (!inc.isInstalled()) {
                    continue;
                }

                // Get the deployment configuration and the target directory
                ProcessGroupConfiguration pgc = inc.getProcessGroupConfiguration();
                if (pgc == null) {
                    String instanceId = inm.getConfiguration().uuid;
                    log.warn("{} / {} - Cannot read persisted process configuration.", instanceId, inm.getKey().getTag());
                    continue;
                }
                DeploymentPathProvider paths = inc.getDeploymentPathProvider();

                // Create controller and add to the affected instance
                InstanceProcessController instanceController = processController.getOrCreate(inm.getUUID());
                instanceController.addProcessGroup(paths, inm.getKey().getTag(), pgc);
            } catch (Exception e) {
                log.error("Cannot setup process control for " + key, e);
            }
        }

        // Check what is running and launch applications
        processController.recover();

        // Startup processes according to their configuration
        processController.setActiveVersions(getState().activeVersions);
        processController.autoStart();
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

}
