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

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.nemesis.antlr.v4.netbeans.v8.maven.output;

import java.util.Set;

import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.execute.RunConfig;

import org.netbeans.modules.maven.api.output.OutputProcessor;
import org.netbeans.modules.maven.api.output.OutputProcessorFactory;
import org.netbeans.modules.maven.output.DefaultOutputProcessorFactory;

import org.openide.util.lookup.ServiceProvider;

/**
 * DefaultOutputProcessorFactory implements interface 
 * ContextOutputProcessorFactory that extends interface OutputProcessorFactory.
 * 
 * @author Frédéric Yvon Vinet
 */
@ServiceProvider(service=OutputProcessorFactory.class)
public class ANTLROutputProcessorFactory extends DefaultOutputProcessorFactory {
    @Override
    public Set<OutputProcessor> createProcessorsSet(Project project) {
//        System.out.println("ANTLROutputProcessorFactory.createProcessorsSet(Project)");
        Set<OutputProcessor> toReturn = super.createProcessorsSet(project);
        if (project != null) {
            toReturn.add(new ANTLROutputProcessor());
        }
        return toReturn;
    }

    @Override
    public Set<? extends OutputProcessor> createProcessorsSet
            (Project project, RunConfig config) {
        return super.createProcessorsSet(project, config);
    }
}
