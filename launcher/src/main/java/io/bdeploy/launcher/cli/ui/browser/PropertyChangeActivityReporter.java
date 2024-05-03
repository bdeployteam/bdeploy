package io.bdeploy.launcher.cli.ui.browser;

import java.beans.PropertyChangeSupport;
import java.util.function.LongSupplier;

import io.bdeploy.common.ActivityReporter;

/**
 * Activity reporter that fires a property change event for each new activity.
 */
class PropertyChangeActivityReporter extends ActivityReporter.Null {

    public static final String ACTIVITY_NAME = "activityName";

    private final PropertyChangeSupport support;

    public PropertyChangeActivityReporter(PropertyChangeSupport support) {
        this.support = support;
    }

    @Override
    public Activity start(String activity) {
        support.firePropertyChange(ACTIVITY_NAME, null, activity);
        return super.start(activity);
    }

    @Override
    public Activity start(String activity, long maxWork) {
        support.firePropertyChange(ACTIVITY_NAME, null, activity);
        return super.start(activity, maxWork);
    }

    @Override
    public Activity start(String activity, LongSupplier maxValue, LongSupplier currentValue) {
        support.firePropertyChange(ACTIVITY_NAME, null, activity);
        return super.start(activity, maxValue, currentValue);
    }
}
