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
package org.aika.neuron;


import org.aika.Activation;
import org.aika.Activation.State;
import org.aika.Iteration;
import org.aika.corpus.Option;
import org.aika.corpus.Range;
import org.aika.lattice.InputNode;
import org.aika.lattice.Node;
import org.aika.neuron.Synapse.Key;
import org.aika.neuron.Synapse.RangeSignal;

import java.util.Collections;


/**
 *
 * @author Lukas Molzberger
 */
public class InputNeuron extends Neuron {


    public InputNeuron() {}


    public InputNeuron(String label) {
        this.label = label;
    }


    public InputNeuron(String label, boolean isBlocked) {
        super(label, isBlocked, true);
    }


    public static InputNeuron create(Iteration t, InputNeuron n) {
        n.m = t.m;

        InputNode node = InputNode.add(t, new Key(false, false, null, 0, false, RangeSignal.NONE, Synapse.RangeVisibility.MATCH_INPUT, RangeSignal.NONE, Synapse.RangeVisibility.MATCH_INPUT), null);
        node.neuron = n;

        n.node = node;
        n.publish(t);

        n.initialized = true;
        return n;
    }


    public void remove(Iteration t) {
        unpublish(t);
    }


    public void propagateAddedActivation(Iteration t, Activation act) {
        State s = new State(1.0, 0, NormWeight.ZERO_WEIGHT);
        act.rounds.set(0, s);
        act.finalState = s;
        act.upperBound = 1.0;
        act.lowerBound = 1.0;

        for(InputNode out: outputNodes.values()) {
            out.addActivation(t, act);
        }
    }


    public void addInput(Iteration t, int begin, int end) {
        addInput(t, begin, end, null, t.doc.bottom);
    }


    public void addInput(Iteration t, int begin, int end, Option o) {
        addInput(t, begin, end, null, o);
    }


    public void addInput(Iteration t, int begin, int end, Integer rid) {
        addInput(t, begin, end, rid, t.doc.bottom);
    }


    public void addInput(Iteration t, int begin, int end, Integer rid, Option o) {
        Node.addActivationAndPropagate(t, new Activation.Key(node, new Range(begin, end), rid, o), Collections.emptySet());

        t.propagate();
    }


    public void removeInput(Iteration t, int begin, int end) {
        removeInput(t, begin, end, null, t.doc.bottom);
    }


    public void removeInput(Iteration t, int begin, int end, Option o) {
        removeInput(t, begin, end, null, o);
    }


    public void removeInput(Iteration t, int begin, int end, Integer rid) {
        removeInput(t, begin, end, rid, t.doc.bottom);
    }


    public void removeInput(Iteration t, int begin, int end, Integer rid, Option o) {
        Range r = new Range(begin, end);
        Activation act = Activation.get(t, node, rid, r, Range.Relation.EQUALS, o, Option.Relation.EQUALS);
        Node.removeActivationAndPropagate(t, act, Collections.emptySet());

        t.propagate();
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("in(");
        sb.append(id);
        if(label != null) {
            sb.append(",");
            sb.append(label);
        }
        sb.append(")");
        return sb.toString();
    }
}
