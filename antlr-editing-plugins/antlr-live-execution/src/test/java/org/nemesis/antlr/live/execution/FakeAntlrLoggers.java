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
package org.nemesis.antlr.live.execution;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import org.nemesis.antlr.memory.spi.AntlrLoggers;

/**
 *
 * @author Tim Boudreau
 */
public final class FakeAntlrLoggers extends AntlrLoggers {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final PrintStream ps = new PrintStream(out);

    private static FakeAntlrLoggers INSTANCE;

    public FakeAntlrLoggers() {
        INSTANCE = this;
    }

    @Override
    public PrintStream forPath(Path path) {
        ps.println("-------------------------- " + path + " --------------------------");
        return ps;
    }

    String get() {
        return new String(out.toByteArray(), UTF_8);
    }

    static String lastText() {
        return INSTANCE != null ? INSTANCE.get() : null;
    }
}
