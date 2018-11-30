package org.nemesis.antlr.v4.netbeans.v8.options;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.AntlrFormatterSettingsPanel;
import org.openide.util.NbBundle.Messages;

final class AntlrPanel extends javax.swing.JPanel {

    private final AntlrOptionsPanelController controller;

    @Messages("formatting=Formatting")
    AntlrPanel(AntlrOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
        AntlrFormatterSettingsPanel pnl = new AntlrFormatterSettingsPanel();
        pnl.setBorder(BorderFactory.createTitledBorder(Bundle.formatting()));
        add(pnl, BorderLayout.CENTER);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    void load() {
        // TODO read settings and initialize GUI
        // Example:        
        // someCheckBox.setSelected(Preferences.userNodeForPackage(AntlrPanel.class).getBoolean("someFlag", false));
        // or for org.openide.util with API spec. version >= 7.4:
        // someCheckBox.setSelected(NbPreferences.forModule(AntlrPanel.class).getBoolean("someFlag", false));
        // or:
        // someTextField.setText(SomeSystemOption.getDefault().getSomeStringProperty());
    }

    void store() {
        // TODO store modified settings
        // Example:
        // Preferences.userNodeForPackage(AntlrPanel.class).putBoolean("someFlag", someCheckBox.isSelected());
        // or for org.openide.util with API spec. version >= 7.4:
        // NbPreferences.forModule(AntlrPanel.class).putBoolean("someFlag", someCheckBox.isSelected());
        // or:
        // SomeSystemOption.getDefault().setSomeStringProperty(someTextField.getText());
    }

    boolean valid() {
        // TODO check whether form is consistent and complete
        return true;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
