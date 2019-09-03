/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.grammar.file.resolver;

import java.util.Optional;
import org.nemesis.antlr.project.FileQuery;
import org.nemesis.antlr.project.Folders;
import org.nemesis.source.spi.RelativeResolverImplementation;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = RelativeResolverImplementation.class, path = "antlr-languages/relative-resolvers/text/x-g4")
public class AntlrFileObjectRelativeResolver extends RelativeResolverImplementation<FileObject> {

    public AntlrFileObjectRelativeResolver() {
        super(FileObject.class);
    }

    @Override
    public Optional<FileObject> resolve(FileObject relativeTo, String name) {
        Folders owner = Folders.ownerOf(relativeTo);
        if (owner == Folders.ANTLR_IMPORTS) {
            return FileQuery.find(name)
                    .forFileObjectsIn(Folders.ANTLR_IMPORTS)
                    .relativeTo(relativeTo);
        } else {
            return FileQuery.find(name).searchingParentOfSearchedForFile()
                    .forFileObjectsIn(Folders.ANTLR_GRAMMAR_SOURCES, Folders.ANTLR_IMPORTS)
                    .relativeTo(relativeTo);
        }
    }
}
