package io.bdeploy.jersey.actions;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jvnet.hk2.annotations.Service;

import io.bdeploy.common.actions.Actions;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.core.SecurityContext;

@Service
public class ActionFactory {

    private static class IntermediateMultiHandle {

        private ActionHandle handle;
        private RuntimeException ex;
    }

    @Inject
    private ActionService actions;

    @Inject
    private Provider<SecurityContext> context;

    public ActionHandle run(Actions action) {
        return run(action, null, null, null);
    }

    public ActionHandle run(Actions action, String group) {
        return run(action, group, null, null);
    }

    public ActionHandle run(Actions action, String group, String instance) {
        return run(action, group, instance, null);
    }

    public ActionHandle run(Actions action, String group, String instance, String item) {
        return runAs(action, group, instance, item, () -> ActionExecution.from(context.get()));
    }

    public ActionHandle runAs(Actions action, String group, String instance, String item, Supplier<ActionExecution> as) {
        return actions.start(new Action(action, group, instance, item), as.get());
    }

    private static IntermediateMultiHandle tryMap(Supplier<ActionHandle> producer) {
        IntermediateMultiHandle h = new IntermediateMultiHandle();
        try {
            h.handle = producer.get();
        } catch (RuntimeException e) {
            h.ex = e;
        }
        return h;
    }

    private static ActionHandle multiHandle(List<IntermediateMultiHandle> handles) {
        List<IntermediateMultiHandle> withError = handles.stream().filter(h -> h.ex != null).collect(Collectors.toList());
        if (!withError.isEmpty()) {
            // we had an exception! close all established handles and throw.
            // this is required to not end up with open action handles.
            // the *most* probable cause for this is that the group, instance of item
            // collection passed to runMulti is not distinct.
            handles.forEach(h -> {
                if (h.handle != null) {
                    h.handle.close();
                }
            });

            RuntimeException first = withError.get(0).ex;
            for (IntermediateMultiHandle h : withError.subList(1, withError.size())) {
                first.addSuppressed(h.ex);
            }
            throw first;
        }

        return () -> handles.stream().forEach(h -> h.handle.close());
    }

    public ActionHandle runMulti(Actions action, String group, Collection<String> instances) {
        return instances.stream().map(i -> tryMap(() -> run(action, group, i)))
                .collect(Collectors.collectingAndThen(Collectors.toList(), ActionFactory::multiHandle));
    }

    public ActionHandle runMulti(Actions action, String group, String instance, Collection<String> items) {
        return items.stream().map(i -> tryMap(() -> run(action, group, instance, i)))
                .collect(Collectors.collectingAndThen(Collectors.toList(), ActionFactory::multiHandle));
    }

    public ActionHandle runMultiAs(Actions action, String group, String instance, Collection<String> items,
            Supplier<ActionExecution> as) {
        return items.stream().map(i -> tryMap(() -> runAs(action, group, instance, i, as)))
                .collect(Collectors.collectingAndThen(Collectors.toList(), ActionFactory::multiHandle));
    }

}
