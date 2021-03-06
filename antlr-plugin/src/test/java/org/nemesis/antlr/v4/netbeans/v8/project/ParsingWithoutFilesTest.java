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
package org.nemesis.antlr.v4.netbeans.v8.project;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.ForeignInvocationEnvironmentTest;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir.projectBaseDir;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.openide.filesystems.FileUtil;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir;
import com.mastfrog.graph.ObjectGraphVisitor;

/**
 *
 * @author Tim Boudreau
 */
public class ParsingWithoutFilesTest {

    private static String goodTestData;
    private static Document goodData;

    @Test
    public void testSematicParse() throws Throwable {
        NBANTLRv4Parser parser = new NBANTLRv4Parser();
        Source src = Source.create(goodData);
        ParsingTestEnvironment.setSourceForParse(src);
        Snapshot sn = src.createSnapshot();
        UserTask ut = new UserTask() {
            @Override
            public void run(ResultIterator ri) throws Exception {
            }
        };
        parser.parse(sn, ut, new SME(src));
        ANTLRv4ParserResult result = parser.getResult(ut);
        assertNotNull(result);
        ANTLRv4SemanticParser sem = result.semanticParser();
        assertNotNull(sem);
        result.extraction().namedRegions(AntlrKeys.RULE_NAMES).forEach(decl -> {
            System.out.println(decl);
        });
    }

    @Test(timeout=30000)
    public void testRuleTrees() throws Throwable {
        Path baseDir = projectBaseDir();
        Path importdir = baseDir.resolve("src/main/antlr4/imports");
        Path lexer = baseDir.resolve("src/main/antlr4/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4Lexer.g4");
//        Path grammar = baseDir.resolve("grammar/grammar_syntax_checking/ANTLRv4.g4");
//        Path grammar = Paths.get("/home/tim/work/foreign/rust-netbeans/src/main/antlr4/com/github/drrb/rust/antlr/Rust.g4");
        Path grammar = TestDir.testResourcePath(TestDir.class, "Rust-Minimal._g4");
//                Paths.get("/home/tim/work/foreign/rust-netbeans/src/main/antlr4/com/github/drrb/rust/antlr/Rust.g4");
//        Path grammar = baseDir.resolve("test/unit/src/org/nemesis/antlr/v4/netbeans/v8/grammar/file/tool/NestedMapGrammar.g4");
        Path output = Paths.get(System.getProperty("java.io.tmpdir"), ForeignInvocationEnvironmentTest.class.getSimpleName() + "-" + System.currentTimeMillis());

        NBANTLRv4Parser parser = new NBANTLRv4Parser();
        Source src = Source.create(FileUtil.toFileObject(grammar.toFile()));
        ParsingTestEnvironment.setSourceForParse(src);
        Snapshot sn = src.createSnapshot();
        UserTask ut = new UserTask() {
            @Override
            public void run(ResultIterator ri) throws Exception {
            }
        };
        parser.parse(sn, ut, new SME(src));
        ANTLRv4ParserResult result = parser.getResult(ut);
        assertNotNull(result);
        ANTLRv4SemanticParser sem = result.semanticParser();
        
        System.out.println("TOP RULES: " + sem.ruleTree().topLevelOrOrphanNodes());
        System.out.println("BOTTOM RULES: " + sem.ruleTree().bottomLevelNodes());

        System.out.println("\n TREE");
        sem.ruleTree().walk(new ObjectGraphVisitor<String>(){
            String indent = "";

            private void indent(int val) {
                char[] c = new char[val*2];
                Arrays.fill(c, ' ');
                indent = new String(c);
            }

            @Override
            public void enterNode(String rule, int depth) {
                System.out.println(indent + " - " + rule);
                indent(depth);
            }

            @Override
            public void exitNode(String rule, int depth) {
                indent(depth);
            }
        });
    }

    public static class SME extends SourceModificationEvent {

        public SME(Source source) {
            super(source, true);
        }

    }

    @Before
    public void setup() throws Throwable {
        ParsingTestEnvironment.init();
        InputStream in = AntlrFolders.class.getResourceAsStream("antlr-options-preview.g4");
        assertNotNull("antlr-options-preview.g4 not on classpath next to AntlrFolders.class",
                in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileUtil.copy(in, out);
        goodTestData = new String(out.toByteArray(), UTF_8);
        goodData = new DefaultStyledDocument();
        goodData.insertString(0, goodTestData, null);
        goodData.putProperty("mimeType", "text/x-g4");
    }
}
