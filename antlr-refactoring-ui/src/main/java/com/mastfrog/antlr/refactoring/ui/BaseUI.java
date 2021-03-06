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
package com.mastfrog.antlr.refactoring.ui;

import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.spi.ui.RefactoringUI;
import org.openide.util.HelpCtx;

/**
 *
 * @author Tim Boudreau
 */
abstract class BaseUI<T extends AbstractRefactoring> implements RefactoringUI {

    protected final T refactoring;

    BaseUI(T refactoring) {
        this.refactoring = refactoring;
    }

    @Override
    public boolean isQuery() {
        return false;
    }

    @Override
    public Problem setParameters() {
        return null;
    }

    @Override
    public Problem checkParameters() {
        return null;
    }

    @Override
    public boolean hasParameters() {
        return false;
    }

    @Override
    public final AbstractRefactoring getRefactoring() {
        return refactoring;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }


}
