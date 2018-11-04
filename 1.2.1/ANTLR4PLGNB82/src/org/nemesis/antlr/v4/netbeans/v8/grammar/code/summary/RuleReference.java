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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary;

import java.util.Objects;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementTarget;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class RuleReference implements RuleElement {
    private final String ruleID;
    private final int    startOffset;
    private final int    endOffset;
    private RuleElementTarget target;

    public String getRuleID() {
        return ruleID;
    }

    public int getStartOffset() {
        return startOffset;
    }
    
    public int getEndOffset() {
        return endOffset;
    }

    public String toString() {
        return ruleID + "@" + startOffset + ":" + endOffset + " (" + kind() + ")";
    }

    public void setTarget(RuleElementTarget target) {
        assert target != null;
        this.target = target;
    }

    public RuleElementKind kind() {
        return target.referenceKind();
    }
    
    public RuleReference
           (RuleElementTarget target, String ruleID     ,
            int    startOffset,
            int    endOffset  ) {
        this.ruleID = ruleID;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.target = target;
    }

    public boolean overlaps(int position) {
        return position >= startOffset && position < endOffset;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 11 * hash + Objects.hashCode(this.ruleID);
        hash = 11 * hash + this.startOffset;
        hash = 11 * hash + this.endOffset;
        hash = 11 * hash + Objects.hashCode(this.target);
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
        final RuleReference other = (RuleReference) obj;
        if (this.startOffset != other.startOffset) {
            return false;
        }
        if (this.endOffset != other.endOffset) {
            return false;
        }
        if (!Objects.equals(this.ruleID, other.ruleID)) {
            return false;
        }
        if (this.target != other.target) {
            return false;
        }
        return true;
    }

    
}