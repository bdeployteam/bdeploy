package io.bdeploy.launcher.cli.ui.browser;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

abstract class SimpleDocumentListener implements DocumentListener {

    protected abstract void onChanged(DocumentEvent e);

    @Override
    public final void insertUpdate(DocumentEvent e) {
        onChanged(e);
    }

    @Override
    public final void removeUpdate(DocumentEvent e) {
        onChanged(e);
    }

    @Override
    public final void changedUpdate(DocumentEvent e) {
        onChanged(e);
    }
}
