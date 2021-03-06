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
package org.nemesis.antlrformatting.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builder for FormattingRule test conditions that logically combine multiple
 * queries of the lexing state, to condition a rule's enablement on particular
 * combination of states configured in the StateBuilder passed when configuring
 * formatting.
 *
 * @see org.nemesis.antlrformatting.api.FormattingRule.whenCombinationOf
 * @see org.nemesis.antlrformatting.api.LexingStateBuilder
 * @author Tim Boudreau
 */
public final class LogicalLexingStateCriteriaBuilder<R> {

    private final List<LogicalBuilder<?, R>> builders = new ArrayList<>();
    private final Function<LogicalLexingStateCriteriaBuilder<R>, R> converter;

    private Consumer<FormattingRule> consumer;

    public LogicalLexingStateCriteriaBuilder(Function<LogicalLexingStateCriteriaBuilder<R>, R> converter) {
        this.converter = converter;
    }

    private void addConsumer(Consumer<FormattingRule> c) {
        if (consumer == null) {
            consumer = c;
        } else {
            consumer = consumer.andThen(c);
        }
    }

    Consumer<FormattingRule> consumer() {
        if (consumer == null) {
            return fr -> {
                throw new IllegalStateException("Nothing built - " + fr);
            };
        }
        return consumer;
    }

    public R then() {
        Predicate<LexingState> result = null;
        if (builders.isEmpty()) {
            throw new IllegalStateException("No conditions added");
        }
        for (LogicalBuilder<?, R> b : builders) {
            result = b.build(result);
        }
        Predicate<LexingState> res = result;
        addConsumer(rule -> {
            rule.addStateCriterion(res);
        });
        return converter.apply(this);
    }

    <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> start(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, false, false, this);
        builders.add(result);
        return result;
    }

    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> or(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, false, false, this);
        builders.add(result);
        return result;
    }

    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> orNot(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, false, true, this);
        builders.add(result);
        return result;
    }

    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> and(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, true, false, this);
        builders.add(result);
        return result;
    }

    public <T extends Enum<T>> LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> andNot(T key) {
        LogicalBuilder<T, R> result = new LogicalBuilder<>(key, true, true, this);
        builders.add(result);
        return result;
    }

    private static class LogicalBuilder<T extends Enum<T>, R> extends LexingStateCriteriaBuilder<T, LogicalLexingStateCriteriaBuilder<R>> {

        private final T key;
        private final boolean isAnd;
        private final boolean isNegated;
        private final LogicalLexingStateCriteriaBuilder<R> parent;
        private OP op;
        private int value = -1;
        private T key2;

        LogicalBuilder(T key, boolean isAnd, boolean isNegated, LogicalLexingStateCriteriaBuilder<R> parent) {
            this.key = key;
            this.isAnd = isAnd;
            this.isNegated = isNegated;
            this.parent = parent;
        }

        Predicate<LexingState> build(Predicate<LexingState> prev) {
            if (key2 != null) {
                Predicate<LexingState> result
                        = new LogicalMultiComponentPredicate<>(op, key, key2);
                if (isNegated) {
                    result = result.negate();
                }
                if (prev != null) {
                    if (isAnd) {
                        result = prev.and(result);
                    } else {
                        result = prev.or(result);
                    }
                }
                return result;
            } else {
                Predicate<LexingState> result
                        = new LogicalComponentPredicate<>(op, key, value);
                if (isNegated) {
                    result = result.negate();
                }
                if (prev != null) {
                    if (isAnd) {
                        result = prev.and(result);
                    } else {
                        result = prev.or(result);
                    }
                }
                return result;
            }
        }

        @Override
        public LogicalLexingStateCriteriaBuilder<R> isGreaterThan(T key) {
            assert key != this.key : "Same key " + key;
            op = OP.GREATER;
            key2 = key;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder<R> isGreaterThanOrEqualTo(T key) {
            assert key != this.key : "Same key " + key;
            op = OP.GREATER_OR_EQUAL;
            key2 = key;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder<R> isEqualTo(T key) {
            op = OP.EQUAL;
            key2 = key;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder<R> isLessThan(T key) {
            assert key != this.key : "Same key " + key;
            op = OP.LESS;
            key2 = key;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder<R> isLessThanOrEqualTo(T key) {
            assert key != this.key : "Same key " + key;
            op = OP.EQUAL;
            key2 = key;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isGreaterThan(int value) {
            op = OP.GREATER;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder<R> isNotEqualTo(T key) {
            assert key != this.key : "Same key " + key;
            op = OP.GREATER;
            this.key2 = key;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isGreaterThanOrEqualTo(int value) {
            op = OP.GREATER_OR_EQUAL;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isLessThan(int value) {
            op = OP.LESS;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isLessThanOrEqualTo(int value) {
            op = OP.LESS_OR_EQUAL;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isEqualTo(int value) {
            op = OP.EQUAL;
            this.value = value;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isUnset() {
            op = OP.UNSET;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isTrue() {
            op = OP.TRUE;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isSet() {
            op = OP.NOT_EQUAL;
            this.value = -1;
            return parent;
        }

        @Override
        public LogicalLexingStateCriteriaBuilder isFalse() {
            op = OP.FALSE;
            return parent;
        }

        private static class LogicalConnector<T> implements Predicate<T> {

            // for logging purposes
            private final Predicate<T> a;
            private final Predicate<? super T> b;
            private final boolean and;

            LogicalConnector(Predicate<T> a, Predicate<? super T> b, boolean and) {
                this.a = a;
                this.b = b;
                this.and = and;
            }

            @Override
            public boolean test(T t) {
                if (and) {
                    return a.test(t) && b.test(t);
                } else {
                    return a.test(t) || b.test(t);
                }
            }

            @Override
            public String toString() {
                if (and) {
                    return a + " & " + b;
                } else {
                    return a + " | " + b;
                }
            }
        }

        private static class LogicalMultiComponentPredicate<T extends Enum<T>> implements Predicate<LexingState> {

            private final OP op;
            private final T key;
            private final T key2;

            LogicalMultiComponentPredicate(OP op, T key, T key2) {
                this.op = op;
                this.key = key;
                this.key2 = key2;
            }

            @Override
            public Predicate<LexingState> or(Predicate<? super LexingState> other) {
                return new LogicalConnector<>(this, other, false);
            }

            @Override
            public Predicate<LexingState> and(Predicate<? super LexingState> other) {
                return new LogicalConnector<>(this, other, true);
            }

            @Override
            public boolean test(LexingState t) {
                switch (op) {
                    case EQUAL:
                        return t.get(key) == t.get(key2);
                    case GREATER:
                        return t.get(key) > t.get(key2);
                    case GREATER_OR_EQUAL:
                        return t.get(key) >= t.get(key2);
                    case LESS:
                        return t.get(key) < t.get(key2);
                    case LESS_OR_EQUAL:
                        return t.get(key) <= t.get(key2);
                    case TRUE:
                        return t.getBoolean(key);
                    case FALSE:
                        return !t.getBoolean(key);
                    case UNSET:
                        return t.get(key) == -1;
                    case NOT_EQUAL:
                        return t.get(key) != t.get(key2);
                    default:
                        throw new AssertionError(op);
                }
            }

            @Override
            public String toString() {
                return key + op.toString() + (op.takesArgument() ? key2 : "");
            }

            public Predicate<LexingState> negate() {
                return new Predicate<LexingState>() {
                    @Override
                    public boolean test(LexingState t) {
                        return !LogicalMultiComponentPredicate.this.test(t);
                    }

                    public String toString() {
                        return "!" + LogicalMultiComponentPredicate.this.toString();
                    }
                };
            }
        }

        private static class LogicalComponentPredicate<T extends Enum<T>> implements Predicate<LexingState> {

            private final OP op;
            private final T key;
            private final int value;

            LogicalComponentPredicate(OP op, T key, int value) {
                this.op = op;
                this.key = key;
                this.value = value;
            }

            @Override
            public Predicate<LexingState> or(Predicate<? super LexingState> other) {
                return new LogicalConnector<>(this, other, false);
            }

            @Override
            public Predicate<LexingState> and(Predicate<? super LexingState> other) {
                return new LogicalConnector<>(this, other, true);
            }

            @Override
            public boolean test(LexingState t) {
                switch (op) {
                    case EQUAL:
                        return t.get(key) == value;
                    case GREATER:
                        return t.get(key) > value;
                    case GREATER_OR_EQUAL:
                        return t.get(key) >= value;
                    case LESS:
                        return t.get(key) < value;
                    case LESS_OR_EQUAL:
                        return t.get(key) <= value;
                    case TRUE:
                        return t.getBoolean(key);
                    case FALSE:
                        return !t.getBoolean(key);
                    case UNSET:
                        return t.get(key) == -1;
                    case NOT_EQUAL:
                        return t.get(key) != value;
                    default:
                        throw new AssertionError(op);
                }
            }

            @Override
            public String toString() {
                return key + op.toString() + (op.takesArgument() ? Integer.toString(value) : "");
            }

            public Predicate<LexingState> negate() {
                return new Predicate<LexingState>() {
                    @Override
                    public boolean test(LexingState t) {
                        return !LogicalComponentPredicate.this.test(t);
                    }

                    public String toString() {
                        return "!" + LogicalComponentPredicate.this.toString();
                    }
                };
            }
        }
    }

}
