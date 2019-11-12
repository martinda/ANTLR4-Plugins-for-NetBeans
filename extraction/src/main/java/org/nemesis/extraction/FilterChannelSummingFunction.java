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
package org.nemesis.extraction;

import org.antlr.v4.runtime.Token;

/**
 *
 * @author Tim Boudreau
 */
final class FilterChannelSummingFunction implements SummingFunction {

    private final SummingFunction delegate;
    private final int channel;

    FilterChannelSummingFunction(SummingFunction delegate, int channel) {
        this.delegate = delegate;
        this.channel = channel;
    }

    @Override
    public long updateSum(long previousValue, int offset, Token token) {
        if (token.getChannel() == channel) {
            return delegate.updateSum(previousValue, offset, token);
        }
        return previousValue;
    }

    @Override
    public void hashInto(Hasher hasher) {
        delegate.hashInto(hasher);
        hasher.writeInt(4007 * (channel + 1));
    }

    @Override
    public String toString() {
        return "Channel(" + channel + " " + delegate + ")";
    }

    public int hashCode() {
        return (channel + 1) * delegate.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof FilterChannelSummingFunction) {
            FilterChannelSummingFunction fc = (FilterChannelSummingFunction) o;
            return fc.channel == channel && fc.delegate == delegate;
        } else {
            return false;
        }
    }
}
