package org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator;

import javax.swing.ListModel;
import javax.swing.event.ListDataListener;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;

/**
 *
 * @author Tim Boudreau
 */
final class EmptyListModel<T> implements ListModel<T> {

    static final ListModel<NamedSemanticRegion<AntlrExtractor.RuleTypes>> EMPTY_MODEL = new EmptyListModel<>();

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public T getElementAt(int index) {
        throw new IndexOutOfBoundsException(index + "");
    }

    @Override
    public void addListDataListener(ListDataListener l) {
        //do nothing
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        // do nothing
    }

}
