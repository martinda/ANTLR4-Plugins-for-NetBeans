/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.instantrename.spi;

import org.nemesis.antlr.instantrename.impl.RenameActionType;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Objects;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.instantrename.impl.RenameQueryResultTrampoline;
import org.nemesis.charfilter.CharFilter;

/**
 * Result of querying a registerer-provided InstantRenamer to determine if a
 * rename is possible.
 *
 * @author Tim Boudreau
 */
public final class RenameQueryResult {

    private final RenameActionType type;
    private final RenameAugmenter augmenter;
    private final RenamePostProcessor postProcessor;
    private final String reason;
    private final CharFilter charFilter;

    RenameQueryResult(RenameActionType type) {
        assert type.isStandalone() : "This constructor should not be called "
                + "for this action type: " + type;
        this.type = notNull("type", type);
        augmenter = null;
        postProcessor = null;
        reason = null;
        charFilter = null;
    }

    RenameQueryResult(String reason) {
        this.reason = reason;
        this.type = RenameActionType.NOT_ALLOWED;
        this.postProcessor = null;
        this.augmenter = null;
        charFilter = null;
    }

    RenameQueryResult(RenameAugmenter augmenter) {
        type = RenameActionType.INPLACE_AUGMENTED;
        this.augmenter = notNull("augmenter", augmenter);
        postProcessor = null;
        this.reason = null;
        charFilter = null;
    }

    RenameQueryResult(RenamePostProcessor postProcessor) {
        this.type = RenameActionType.POST_PROCESS;
        this.augmenter = null;
        this.postProcessor = notNull("postProcessor", postProcessor);
        this.reason = null;
        charFilter = null;
    }

    RenameQueryResult(RenameQueryResult orig, CharFilter filter) {
        this.type = orig.type;
        this.postProcessor = orig.postProcessor;
        this.reason = orig.reason;
        this.augmenter = orig.augmenter;
        this.charFilter = filter;
    }

    /**
     * Adds a filter to this inplace renamer to cause some typed character to
     * have no effect (if the filter's test method returns false for them).
     *
     * @param filter A filter
     * @return A query result
     */
    public RenameQueryResult withCharFilter(CharFilter filter) {
        return new RenameQueryResult(this, notNull("filter", filter));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                RenameQueryResult.class.getSimpleName()).append('(')
                .append(type.name().toLowerCase());
        switch (type) {
            case USE_REFACTORING_API:
            case INPLACE:
                break;
            case INPLACE_AUGMENTED:
                sb.append(" augmenter=").append(augmenter);
                break;
            case NOT_ALLOWED:
                sb.append(" reason=").append(reason);
                break;
            case POST_PROCESS:
                sb.append(" postProcess=").append(postProcessor);
                break;
            case NOTHING_FOUND:
                sb.append(" nothing-found");
                break;
            default:
                throw new AssertionError(type);
        }
        if (charFilter != null) {
            sb.append(" charFilter=").append(charFilter);
        }
        return sb.append(')').toString();
    }

    void onRenameCompleted(String original, String nue, Runnable undo) {
        if (augmenter != null) {
            augmenter.completed();
        }
        if (postProcessor != null) {
            postProcessor.onRenameCompleted(original, nue, undo);
        }
    }

    void nameUpdated(String orig, String newName, StyledDocument doc) {
        if (augmenter != null) {
            augmenter.nameUpdated(orig, newName, doc);
        }
    }

    void cancelled() {
        if (augmenter != null) {
            augmenter.cancelled();
        }
        if (postProcessor != null) {
            postProcessor.cancelled();
        }
    }

    boolean testChar(boolean initial, char typed) {
        return charFilter == null ? true : charFilter.test(initial, typed);
    }

    RenameActionType type() {
        return type;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.type);
        hash = 31 * hash + Objects.hashCode(this.augmenter);
        hash = 31 * hash + Objects.hashCode(this.postProcessor);
        hash = 31 * hash + Objects.hashCode(this.reason);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RenameQueryResult other = (RenameQueryResult) obj;
        if (!Objects.equals(this.reason, other.reason)) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        if (!Objects.equals(this.augmenter, other.augmenter)) {
            return false;
        }
        return Objects.equals(this.postProcessor, other.postProcessor);
    }

    static class TrampolineImpl extends RenameQueryResultTrampoline {

        private static final RenameQueryResult VETO = new RenameQueryResult(RenameActionType.NOT_ALLOWED);
        private static final RenameQueryResult PROCEED = new RenameQueryResult(RenameActionType.INPLACE);
        private static final RenameQueryResult USE_REFACTORING = new RenameQueryResult(RenameActionType.USE_REFACTORING_API);
        private static final RenameQueryResult NOTHING = new RenameQueryResult(RenameActionType.NOTHING_FOUND);

        @Override
        protected void _onRename(RenameQueryResult res, String original, String nue, Runnable undo) {
            res.onRenameCompleted(original, nue, undo);
        }

        @Override
        protected void _nameUpdated(RenameQueryResult res, String orig, String newName, StyledDocument doc) {
            res.nameUpdated(orig, newName, doc);
        }

        @Override
        protected void _cancelled(RenameQueryResult res) {
            res.cancelled();
        }

        @Override
        protected RenameActionType _typeOf(RenameQueryResult res) {
            return res.type();
        }

        @Override
        protected RenameQueryResult _veto(String reason) {
            return reason == null ? VETO : new RenameQueryResult(reason);
        }

        @Override
        protected RenameQueryResult _proceed() {
            return PROCEED;
        }

        @Override
        protected RenameQueryResult _nothingFound() {
            return NOTHING;
        }

        @Override
        protected RenameQueryResult _useRefactoring() {
            return USE_REFACTORING;
        }

        @Override
        protected RenameQueryResult _augment(RenameAugmenter aug) {
            return new RenameQueryResult(aug);
        }

        @Override
        protected RenameQueryResult _postProcess(RenamePostProcessor postProcessor) {
            return new RenameQueryResult(postProcessor);
        }

        @Override
        protected boolean _testChar(RenameQueryResult res, boolean initial, char typed) {
            return res.testChar(initial, typed);
        }
    }

    static {
        RenameQueryResultTrampoline.DEFAULT = new TrampolineImpl();
    }
}
