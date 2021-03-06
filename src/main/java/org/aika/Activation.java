/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aika;


import org.aika.corpus.Document;
import org.aika.corpus.ExpandNode.StateChange;
import org.aika.corpus.Option;
import org.aika.corpus.Range;
import org.aika.lattice.Node;
import org.aika.neuron.Neuron;
import org.aika.neuron.Neuron.NormWeight;
import org.aika.neuron.Synapse;

import java.util.*;

/**
 *
 * @author Lukas Molzberger
 */
public class Activation implements Comparable<Activation> {

    public final Key key;

    public boolean isReplaced;
    public boolean isRemoved;
    public int removedId;
    public long visitedNeuronTrain = -1;

    public static int removedIdCounter = 1;
    public static long visitedCounter = 1;

    public double upperBound;
    public double lowerBound;

    public Rounds rounds = new Rounds();

    public State finalState;

    public long currentStateV = -1;
    public StateChange currentStateChange;

    public boolean isTrainingAct;

    public double initialErrorSignal;
    public double errorSignal;

    public TreeMap<Key, Activation> inputs = new TreeMap<>();
    public TreeMap<Key, Activation> outputs = new TreeMap<>();
    public TreeSet<SynapseActivation> neuronInputs;
    public TreeSet<SynapseActivation> neuronOutputs;

    public boolean ubQueued = false;
    public boolean isQueued = false;
    public long queueId;


    public Activation(Key key) {
        this.key = key;
    }


    public Activation(Node n, Range pos, Integer rid, Option o) {
        key = new Key(n, pos, rid, o);
    }


    public void link(Collection<Activation> inputActs) {
        for(Activation iAct: inputActs) {
            inputs.put(iAct.key, iAct);
            iAct.outputs.put(key, this);
        }
    }


    public void unlink(Collection<Activation> inputActs) {
        for (Activation iAct : inputActs) {
            inputs.remove(iAct.key);
            iAct.outputs.remove(key);
        }
    }


    public void unlink() {
        for (Activation act : inputs.values()) {
            act.outputs.remove(key);
        }
        for (Activation act : outputs.values()) {
            act.inputs.remove(key);
        }
    }


    public void register(Iteration t) {
        Node.ThreadState th = key.n.getThreadState(t);
        if (th.activations.isEmpty()) {
            (isTrainingAct ? t.activatedNodesForTraining : t.activatedNodes).add(key.n);
        }
        th.activations.put(key, this);

        TreeMap<Key, Activation> actEnd = th.activationsEnd;
        if(actEnd != null) actEnd.put(key, this);

        TreeMap<Key, Activation> actRid = th.activationsRid;
        if(actRid != null) actRid.put(key, this);

        if(key.o.activations == null) {
            key.o.activations = new TreeMap<>();
        }
        key.o.activations.put(key, this);

        if(key.n.neuron != null) {
            if(key.o.neuronActivations == null) {
                key.o.neuronActivations = new TreeSet<>();
            }
            key.o.neuronActivations.add(this);
        }
    }


    public void unregister(Iteration t) {
        assert !key.o.activations.isEmpty();

        Node.ThreadState th = key.n.getThreadState(t);

        th.activations.remove(key);

        TreeMap<Key, Activation> actEnd = th.activationsEnd;
        if(actEnd != null) actEnd.remove(key);

        TreeMap<Key, Activation> actRid = th.activationsRid;
        if(actRid != null) actRid.remove(key);

        if(th.activations.isEmpty()) {
            (isTrainingAct ? t.activatedNodesForTraining : t.activatedNodes).remove(key.n);
        }

        key.o.activations.remove(key);
        if(key.n.neuron != null) {
            key.o.neuronActivations.remove(this);
        }
    }


    public static Activation get(Iteration t, Node n, Integer rid, Range r, Range.Relation rr, Option o, Option.Relation or) {
        for(Activation act: select(t, n, rid, r, rr, o, or)) {
            return act;
        }
        return null;
    }


    public static Activation get(Iteration t, Node n, Key ak) {
        return get(t, n, ak.rid, ak.r, Range.Relation.EQUALS, ak.o, Option.Relation.EQUALS);
    }


    public static Activation getNextSignal(Node n, Iteration t, int from, Integer rid, Option o, boolean dir, boolean inv) {
        Node.ThreadState th = n.getThreadState(t);
        Key bk = new Key(null, new Range(from, dir ? Integer.MIN_VALUE : Integer.MAX_VALUE).invert(inv), rid, o);
        NavigableMap<Key, Activation> tmp = (inv ? th.activationsEnd : th.activations);
        tmp = dir ? tmp.descendingMap() : tmp;
        for(Activation act: tmp.tailMap(bk, false).values()) {
            if(act.filter(n, rid, null, null, o, Option.Relation.CONTAINED_IN)) {
                return act;
            }
        }
        return null;
    }


    public static List<Activation> select(Iteration t, Node n, Integer rid, Range r, Range.Relation rr, Option o, Option.Relation or) {
        assert n != null;

        Node.ThreadState th = n.getThreadState(t);
        int s = th.activations.size();

        if(s == 0) return Collections.emptyList();
        else if(s == 1) {
            Activation act = th.activations.firstEntry().getValue();
            if (act.filter(n, rid, r, rr, o, or)) return Collections.singletonList(act);
            else return Collections.emptyList();
        }

        List<Activation> results = new ArrayList<>();

        if(n != null) {
            if(rid != null) {
                Key bk = new Key(n, Range.MIN, rid, Option.MIN);
                Key ek = new Key(n, Range.MAX, rid, Option.MAX);

                for (Activation act : th.activationsRid.subMap(bk, true, ek, true).values()) {
                    if (act.filter(n, rid, r, rr, o, or)) {
                        results.add(act);
                    }
                }
            } else {
                boolean returnFirstOnly = false;
                if(rr == null) {
                    for (Activation act : th.activations.values()) {
                        if (act.filter(n, rid, r, rr, o, or)) {
                            results.add(act);
                        }
                        if(returnFirstOnly) break;
                    }
                } else {
                    rr.getActivations(results, t, n, rid, r, o, or);
                }
            }
        } else {
            assert false;
        }

        return results;
    }


    public boolean filter(Node n, Integer rid, Range r, Range.Relation rr, Option o, Option.Relation or) {
        return (n != null || key.n == n) && (rid == null || (key.rid != null && key.rid.intValue() == rid.intValue())) && (r == null || rr == null || (rr.match(key.r, r))) && (o == null || or.compare(key.o, o));
    }


    public String toString(Document doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ACT ");
        sb.append(",(");
        sb.append(key.r);
        sb.append("),");
        sb.append(doc.getContent().substring(Math.max(0, key.r.begin - 3), Math.min(doc.length(), key.r.end + 3)));
        sb.append(",");
        sb.append(key.n);
        sb.append(">");
        return sb.toString();
    }


    @Override
    public int compareTo(Activation act) {
        return key.compareTo(act.key);
    }


    public static int compare(Activation a, Activation b) {
        if(a == b) return 0;
        if(a == null && b != null) return -1;
        if(a != null && b == null) return 1;
        return a.compareTo(b);
    }


    public static final class Key implements Comparable<Key> {
        public final Node n;
        public final Range r;
        public final Integer rid;
        public final Option o;

        private int refCount = 0;


        public Key(Node n, Range r, Integer rid, Option o) {
            this.n = n;
            this.r = r;
            this.rid = rid;
            this.o = o;
            countRef();
            if(o != null) {
                o.countRef();
            }
        }


        public void countRef() {
            refCount++;
        }


        public void releaseRef() {
            assert refCount > 0;
            refCount--;
            if(refCount == 0) {
                o.releaseRef();
            }
        }


        @Override
        public int compareTo(Key k) {
            int x = n.compareTo(k.n);
            if(x != 0) return x;
            x = Range.compare(r, k.r, false);
            if(x != 0) return x;
            x = Utils.compareInteger(rid, k.rid);
            if(x != 0) return x;
            return o.compareTo(k.o);
        }


        public String toString() {
            return (n != null ? n.id + (n.neuron != null && n.neuron.label != null ? ":" + n.neuron.label : "") + " " : "") + r + " " + rid + " " + o;
        }
    }


    public static class State {
        public final double value;
        public final int fired;
        public final NormWeight weight;

        public static final State ZERO = new State(0.0, -1, NormWeight.ZERO_WEIGHT);

        public State(double value, int fired, NormWeight weight) {
            this.value = value;
            this.fired = fired;
            this.weight = weight;
        }

        public boolean equals(State s) {
            return Math.abs(value - s.value) <= Neuron.WEIGHT_TOLERANCE;
        }

        public boolean equalsWithWeights(State s) {
            return equals(s) && weight.equals(s.weight);
        }

        public String toString() {
            return "V:" + value;
        }
    }


    public static class Rounds {
        TreeMap<Integer, State> rounds = new TreeMap<>();

        public boolean set(int r, State s) {
            State lr = get(r - 1);
            if(lr != null && lr.equalsWithWeights(s)) {
                State or = rounds.get(r);
                if(or != null) {
                    rounds.remove(r);
                    return !or.equalsWithWeights(s);
                }
                return false;
            } else {
                State or = rounds.put(r, s);

                for(Iterator<Map.Entry<Integer, State>> it = rounds.tailMap(r + 1).entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Integer, State> me = it.next();
                    if(me.getValue().equalsWithWeights(s)) it.remove();
                }
                return or == null || !or.equalsWithWeights(s);
            }
        }

        public State get(int r) {
            Map.Entry<Integer, State> me = rounds.floorEntry(r);
            return me != null ? me.getValue() : State.ZERO;
        }

        public Rounds copy() {
            Rounds nr = new Rounds();
            nr.rounds.putAll(rounds);
            return nr;
        }

        public Integer getLastRound() {
            return !rounds.isEmpty() ? rounds.lastKey() : null;
        }

        public State getLast() {
            return !rounds.isEmpty() ? rounds.lastEntry().getValue() : null;
        }
    }


    public static class SynapseActivation {
        public final Synapse s;
        public final Activation input;
        public final Activation output;

        public static Comparator<SynapseActivation> INPUT_COMP = new Comparator<SynapseActivation>() {
            @Override
            public int compare(SynapseActivation sa1, SynapseActivation sa2) {
                int r = Synapse.INPUT_SYNAPSE_COMP.compare(sa1.s, sa2.s);
                if(r != 0) return r;
                return sa1.input.compareTo(sa2.input);
            }
        };

        public static Comparator<SynapseActivation> OUTPUT_COMP = new Comparator<SynapseActivation>() {
            @Override
            public int compare(SynapseActivation sa1, SynapseActivation sa2) {
                int r = Synapse.OUTPUT_SYNAPSE_COMP.compare(sa1.s, sa2.s);
                if(r != 0) return r;
                return sa1.output.compareTo(sa2.output);
            }
        };


        public SynapseActivation(Synapse s, Activation input, Activation output) {
            this.s = s;
            this.input = input;
            this.output = output;
        }
    }

}
