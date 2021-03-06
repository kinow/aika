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
package org.aika.network;


import org.aika.Activation;
import org.aika.Iteration;
import org.aika.Iteration.Input;
import org.aika.Model;
import org.aika.corpus.Document;
import org.aika.corpus.Option;
import org.aika.corpus.Range;
import org.aika.lattice.AndNode;
import org.aika.lattice.Node;
import org.aika.neuron.InputNeuron;
import org.aika.neuron.Neuron;
import org.junit.Test;

import java.util.Collections;

/**
 *
 * @author Lukas Molzberger
 */
public class OrOptionsTest {

    @Test
    public void testOrOptions() {
        Model m = new Model();
        Document doc = Document.create("aaaaaaaaaa");
        Iteration t = m.startIteration(doc, 0);

        AndNode.minFrequency = 5;

        InputNeuron inA = t.createOrLookupInputSignal("A");
        InputNeuron inB = t.createOrLookupInputSignal("B");
        InputNeuron inC = t.createOrLookupInputSignal("C");

        Neuron pD = new Neuron("D");

        t.createOrNeuron(pD,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0)
                        .setRecurrent(false)
                        .setMinInput(1.0),
                new Input()
                        .setNeuron(inB)
                        .setWeight(1.0)
                        .setRecurrent(false)
                        .setMinInput(1.0),
                new Input()
                        .setNeuron(inC)
                        .setWeight(1.0)
                        .setRecurrent(false)
                        .setMinInput(1.0)
        );

        Option o0 = Option.addPrimitive(doc);
        Range r = new Range(0, 10);
        Node.addActivationAndPropagate(t, new Activation.Key(inA.node, r, 0, o0), Collections.emptySet());
        t.propagate();

        Option o1 = Option.addPrimitive(doc);
        Node.addActivationAndPropagate(t, new Activation.Key(inA.node, r, 0, o1), Collections.emptySet());
        t.propagate();

        Option o2 = Option.addPrimitive(doc);
        Node.addActivationAndPropagate(t, new Activation.Key(inA.node, r, 0, o2), Collections.emptySet());
        t.propagate();


        System.out.println(t.networkStateToString(true, true));
    }
}
