package io.bdeploy.bhive.cli;

import java.nio.file.Paths;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.IntStream;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.TreeTool.TreeConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.ManifestRefView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeDiff;
import io.bdeploy.bhive.objects.view.scanner.TreeElementDiff;
import io.bdeploy.bhive.objects.view.scanner.TreeElementDiff.DifferenceType;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ObjectSizeOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.util.UnitHelper;

/**
 * A tool to list and diff {@link Manifest} {@link Tree}s.
 */
@Help("Query and diff manifest root trees in the given BHive")
@CliName("tree")
public class TreeTool extends ConfiguredCliTool<TreeConfig> {

    private static final String EMPTY_ID = "0000000000000000000000000000000000000000";

    public @interface TreeConfig {

        @Help("The BHive to use. Alternatively use --remote.")
        @EnvironmentFallback("BHIVE")
        String hive();

        @Help("List available manifests")
        String list();

        @Help("Give two manifests to diff.")
        String[] diff() default {};
    }

    public TreeTool() {
        super(TreeConfig.class);
    }

    @Override
    protected void run(TreeConfig config) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {
            if (config.list() != null) {
                TreeView snap = hive.execute(new ScanOperation().setManifest(Manifest.Key.parse(config.list())));
                format(snap);
            } else if (config.diff().length > 0) {
                if (config.diff().length != 2) {
                    helpAndFail("Currently only support diff of two manifests");
                }
                TreeView t1 = hive.execute(new ScanOperation().setManifest(Manifest.Key.parse(config.diff()[0])));
                TreeView t2 = hive.execute(new ScanOperation().setManifest(Manifest.Key.parse(config.diff()[1])));

                format(new TreeDiff(t1, t2).diff(), t1, hive);
            }
        }
    }

    private void format(List<TreeElementDiff> diff, TreeView original, BHive hive) {
        for (TreeElementDiff ted : diff) {
            switch (ted.getType()) {
                case CONTENT_DIFF:
                    out().println("C: [" + ted.getLeftType().name().charAt(0) + "]" + ted.getLeft().getElementId() + " : ["
                            + ted.getRightType().name().charAt(0) + "]" + ted.getRight().getElementId() + " "
                            + ted.getLeft().getPathString());
                    break;
                case ONLY_LEFT:
                    out().println("L: [" + ted.getLeftType().name().charAt(0) + "]" + ted.getLeft().getElementId() + " : [ ]"
                            + EMPTY_ID + " " + ted.getLeft().getPathString());
                    break;
                case ONLY_RIGHT:
                    out().println("R: [ ]" + EMPTY_ID + " : [" + ted.getRightType().name().charAt(0) + "]"
                            + ted.getRight().getElementId() + " " + ted.getRight().getPathString());
                    break;
            }
        }

        // count size difference when updating to right - collect all only right or diff, but exclude objects with existing hash
        SortedSet<ObjectId> existingObjs = new TreeSet<>();
        Function<ElementView, Boolean> enlist = (ev) -> {
            existingObjs.add(ev.getElementId());
            return true;
        };
        original.visit(
                new TreeVisitor.Builder().onBlob(enlist::apply).onTree(enlist::apply).onManifestRef(enlist::apply).build());

        ObjectSizeOperation oso = new ObjectSizeOperation();
        diff.stream().filter(d -> d.getType() != DifferenceType.ONLY_LEFT).map(d -> d.getRight().getElementId())
                .filter(o -> !existingObjs.contains(o)).forEach(oso::addObject);

        Long size = hive.execute(oso);

        out().println("updating from left to right would require a " + UnitHelper.formatFileSize(size) + " transfer.");
    }

    private void format(TreeView snap) {
        snap.visit(new TreeVisitor.Builder().onBlob(b -> {
            indent(b);
            out().println("[B] " + verbose(b));
        }).onTree(t -> {
            indent(t);
            out().println("[T] " + verbose(t)
                    + ((t instanceof ManifestRefView) ? (" {R:" + ((ManifestRefView) t).getReferenced() + "}") : ""));
            return true;
        }).onMissing(m -> {
            indent(m);
            out().println("[X] " + verbose(m) + " {MISSING}");
        }).build());
    }

    private String verbose(ElementView x) {
        StringBuilder builder = new StringBuilder();
        builder.append(x.getName());
        if (isVerbose()) {
            IntStream.range(0, Math.max(1, 80 - (x.getPath().size() * 2) - x.getName().length()))
                    .forEach(i -> builder.append(' '));
            builder.append(x.getElementId());
        }
        return builder.toString();
    }

    private void indent(ElementView e) {
        e.getPath().forEach(x -> out().print("  "));
    }

}