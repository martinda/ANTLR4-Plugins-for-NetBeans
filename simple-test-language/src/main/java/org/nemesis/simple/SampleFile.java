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
package org.nemesis.simple;

import java.io.IOException;
import java.io.InputStream;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;

/**
 * For tests, an interface to allow provision of sample files for testing.
 *
 * @author Tim Boudreau
 */
public interface SampleFile<L extends Lexer, P extends Parser> {

    CharStream charStream() throws IOException;

    InputStream inputStream();

    int length() throws IOException;

    L lexer() throws IOException;

    L lexer(ANTLRErrorListener l) throws IOException;

    P parser() throws IOException;

    String text() throws IOException;
}
