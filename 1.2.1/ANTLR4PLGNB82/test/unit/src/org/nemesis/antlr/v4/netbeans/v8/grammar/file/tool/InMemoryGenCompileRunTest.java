package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.junit.After;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.RecompilationTest.TEXT_1;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.RecompilationTest.TEXT_2;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.TestDir.projectBaseDir;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.GenerateBuildAndRunGrammarResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.ParseProxyBuilder;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class InMemoryGenCompileRunTest {

    private Path importdir;
    private Path lexer;
    private Path grammar;
    private String sampleGrammar;
    private Path tokens;
    private Path interp;
    private Path lextokens;
    private Path lexinterp;
    private Path tmpFile;

    @Test
    public void testSimpleGrammar() throws Throwable {
        InMemoryAntlrSourceGenerationBuilder bldr = new InMemoryAntlrSourceGenerationBuilder(tmpFile)
                .setAntlrLibrary(new UnitTestAntlrLibrary());
        ParseProxyBuilder runner = bldr.toParseAndRunBuilder();

        GenerateBuildAndRunGrammarResult result = runner.parse(TEXT_1);

        System.out.println(result.parseResult().get().parseTree().get());

        GenerateBuildAndRunGrammarResult result2 = runner.parse(TEXT_2);

        System.out.println(result2.parseResult().get().parseTree().get());
        assertFalse(result2.wasCompiled());
        assertTrue(result2.wasParsed());

        GenerateBuildAndRunGrammarResult result3 = runner.parse(TEXT_2);
        assertSame(result2, result3);
    }

    @Test
    public void testParseLexerGrammar() throws Throwable {
        Path g = importdir.resolve("LexBasic.g4");
        InMemoryAntlrSourceGenerationBuilder bldr = new InMemoryAntlrSourceGenerationBuilder(g)
                .setAntlrLibrary(new UnitTestAntlrLibrary());
        ParseProxyBuilder runner = bldr.toParseAndRunBuilder();

        GenerateBuildAndRunGrammarResult result = runner.parse(sampleGrammar);
        if (result.thrown().isPresent()) {
            System.out.println("\nFAILED.  FS CONTENTS:\n");
            bldr.jfs().listAll((loc, fo) -> {
                System.out.println(" - " + loc + " - " + fo);
            });
        }
    }

    @Test
    public void testGenerateAndInvoke() throws Throwable {
        InMemoryAntlrSourceGenerationBuilder bldr = new InMemoryAntlrSourceGenerationBuilder(grammar)
                .withImportDir(importdir)
                .mapIntoSourcePackage(lexer)
                .mapIntoSourcePackage(grammar)
                .copyIntoSourcePackage(tokens)
                .copyIntoSourcePackage(interp)
                .copyIntoSourcePackage(lextokens)
                .copyIntoSourcePackage(lexinterp)
                .setAntlrLibrary(new UnitTestAntlrLibrary());

        ParseProxyBuilder runner = bldr.toParseAndRunBuilder();
        GenerateBuildAndRunGrammarResult result = runner.parse(sampleGrammar);

        if (result.thrown().isPresent()) {
            System.out.println("\nFAILED.  FS CONTENTS:\n");
            bldr.jfs().listAll((loc, fo) -> {
//                if (fo.getName().endsWith(".java") || fo.getName().endsWith(".class")) {
                System.out.println(" - " + loc + " - " + fo);
                if (fo.getName().contains("ParserExtr")) {
                    try {
                        System.out.println(fo.getCharContent(true));
//                }
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
        }

        result.rethrow();
        assertTrue(result.parseResult().isPresent());
        assertTrue(result.parseResult().get().parseTree().isPresent());
        ParseTreeProxy prx = result.parseResult().get().parseTree().get();
        System.out.println("PRX: " + prx);
    }

    @Before
    public void setup() throws URISyntaxException, IOException {
        Path baseDir = projectBaseDir();
        importdir = baseDir.resolve("grammar/imports");
        lexer = baseDir.resolve("grammar/grammar_syntax_checking/ANTLRv4Lexer.g4");
        grammar = baseDir.resolve("grammar/grammar_syntax_checking/ANTLRv4.g4");
        tokens = baseDir.resolve("src/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4.tokens");
        interp = baseDir.resolve("src/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4.interp");
        lextokens = baseDir.resolve("src/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4Lexer.tokens");
        lexinterp = baseDir.resolve("src/org/nemesis/antlr/v4/netbeans/v8/grammar/code/checking/impl/ANTLRv4Lexer.interp");
        assertTrue(tokens + "", Files.exists(tokens));
        assertTrue(interp + "", Files.exists(interp));
        try (InputStream in = InMemoryGenCompileRunTest.class.getResourceAsStream("NestedMapGrammar.g4")) {
            assertNotNull(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            FileUtil.copy(in, out);
            sampleGrammar = new String(out.toByteArray(), UTF_8);
        }
        tmpFile = Paths.get(System.getProperty("java.io.tmpdir"), "SampleGrammar.g4");
        Files.write(tmpFile, sampleGrammar.getBytes(UTF_8), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }

    @After
    public void cleanup() throws IOException {
        if (tmpFile != null && Files.exists(tmpFile)) {
            Files.delete(tmpFile);
        }
    }
}
