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
package org.nemesis.antlr.spi.language.highlighting.semantic;

import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;
import java.util.function.Function;
import javax.swing.text.AttributeSet;
import org.nemesis.antlr.spi.language.highlighting.ColoringCategory;

/**
 * Allows fields of types RegionKey, NameReferenceSetKey, or NamedRegionKey to
 * be registered for custom syntax highlighting; and allows for
 * custom colorings to be registered for highlighting.
 * <i>Specify only one of <code>coloringName()</code>,
 * <code>colorFinder()</code> or <code>attributeSetFinder()</code>; or
 * <code>coloration()</code>. Types returned by colorFinder() or
 * attributeFinder() must have a default public no-arg constructor and be
 * public.
 * <p>
 * Note:  For NamedSemanticRegions, if their Enum kind type implements
 * Supplier&lt;String&gt;, the result of its get() method will be used
 * as the coloring lookup, with - for fixed*() methods, the default passed
 * as a fallback.
 * </p>
 *
 * @author Tim Boudreau
 */
@Retention(SOURCE)
@Target(FIELD)
public @interface HighlighterKeyRegistration {

    /**
     * The mime type to register this key for.
     *
     * @return A mime type
     */
    String mimeType();

    /**
     * Use a single coloring name for all matched keys.
     *
     * @return A coloring name
     */
    String coloringName() default "";

    /**
     * The precedence of this highlight among others.
     *
     * @return Typically a multiple of 100 so other modules can insert.
     */
    int order() default 0;

    /**
     * Use the function type returned here to map a semantic region, or a named
     * semantic region or named reference to a coloring name. The returned type
     * must be public and have a public no-argument constructor.
     * <p>
     * <i>
     * If this attribute is used, do not use coloringName(),
     * attributeSetFinder() or coloration().
     * </i></p>
     *
     * @return A function type; must take as an argument a key
     * (NameReferenceSetKey, RegionKey or NamedRegionKey) parameterized on the
     * same type as the key this annotation is applied to.
     */
    Class<? extends Function<?, String>> colorFinder()
            default DummyKeyFinder.class;

    /**
     * Use the function type returned here to map a semantic region, or a named
     * semantic region or named reference to a coloring name. The returned type
     * must be public and have a public no-argument constructor.
     * <p>
     * <i>
     * If this attribute is used, do not use coloringName(), colorFinder() or
     * coloration().
     * </i></p>
     *
     * @return A function type
     */
    Class<? extends Function<?, AttributeSet>> attributeSetFinder()
            default DummyAttributeSetFinder.class;

    /**
     * Define a coloration that should be used for this rule. The defined
     * colorings will be generated into the coloring theme(s) for this language.
     *
     * @return A coloring category
     */
    ColoringCategory colors() default @ColoringCategory(name = "", colors = {});

    /**
     * Determines whether re-parse, re-highlight is triggered every time the
     * caret is moved, or only when the document has changed.
     *
     * @return A trigger condition
     */
    HighlightRefreshTrigger trigger() default HighlightRefreshTrigger.DOCUMENT_CHANGED;

    /**
     * Determine the z-order rack for this highlighting, which determines the
     * order in which highlightings may override each other.
     *
     * @return A z order
     */
    HighlightZOrder zOrder() default HighlightZOrder.SYNTAX_RACK;

    /**
     * Determine the z-order within the rack returned by zOrder() in which this
     * highlighting should be sorted.
     *
     * @return
     */
    int positionInZOrder() default 0;

    /**
     * Parameter to HighlightsLayer.create().
     *
     * @return Fixed size or not
     */
    boolean fixedSize() default true;
}
