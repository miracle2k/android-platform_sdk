
package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.common.rendering.api.Capability;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder.Reference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import java.util.List;

/**
 * Action which creates a submenu for the "Show Included In" action
 */
class ShowWithinMenuAction extends Action implements MenuListener, IMenuCreator {
    private Menu mMenu;

    private LayoutEditor mLayoutEditor;

    ShowWithinMenuAction(LayoutEditor layoutEditor) {
        super("Show Included In", IAction.AS_DROP_DOWN_MENU);
        mLayoutEditor = layoutEditor;
    }

    @Override
    public IMenuCreator getMenuCreator() {
        return this;
    }

    public void dispose() {
        if (mMenu != null) {
            mMenu.dispose();
            mMenu = null;
        }
    }

    public Menu getMenu(Control parent) {
        return null;
    }

    public Menu getMenu(Menu parent) {
        mMenu = new Menu(parent);
        mMenu.addMenuListener(this);
        return mMenu;
    }

    public void menuHidden(MenuEvent e) {
    }

    public void menuShown(MenuEvent e) {
        MenuItem[] menuItems = mMenu.getItems();
        for (int i = 0; i < menuItems.length; i++) {
            menuItems[i].dispose();
        }

        GraphicalEditorPart graphicalEditor = mLayoutEditor.getGraphicalEditor();
        IFile file = graphicalEditor.getEditedFile();
        if (graphicalEditor.renderingSupports(Capability.EMBEDDED_LAYOUT)) {
            IProject project = file.getProject();
            IncludeFinder finder = IncludeFinder.get(project);
            final List<Reference> includedBy = finder.getIncludedBy(file);

            if (includedBy != null && includedBy.size() > 0) {
                for (final Reference reference : includedBy) {
                    String title = reference.getDisplayName();
                    IAction action = new ShowWithinAction(title, reference);
                    new ActionContributionItem(action).fill(mMenu, -1);
                }
                new Separator().fill(mMenu, -1);
            }
            IAction action = new ShowWithinAction("Nothing", null);
            if (includedBy == null || includedBy.size() == 0) {
                action.setEnabled(false);
            }
            new ActionContributionItem(action).fill(mMenu, -1);
        } else {
            IAction action = new ShowWithinAction("Not supported on platform", Reference
                    .create(file));
            action.setEnabled(false);
            action.setChecked(false);
            new ActionContributionItem(action).fill(mMenu, -1);
        }
    }

    /** Action to select one particular include-context */
    private class ShowWithinAction extends Action {
        private Reference mReference;

        public ShowWithinAction(String title, Reference reference) {
            super(title, IAction.AS_RADIO_BUTTON);
            mReference = reference;
        }

        @Override
        public boolean isChecked() {
            Reference within = mLayoutEditor.getGraphicalEditor().getIncludedWithin();
            if (within == null) {
                return mReference == null;
            } else {
                return within.equals(mReference);
            }
        }

        @Override
        public void run() {
            if (!isChecked()) {
                mLayoutEditor.getGraphicalEditor().showIn(mReference);
            }
        }
    }
}
