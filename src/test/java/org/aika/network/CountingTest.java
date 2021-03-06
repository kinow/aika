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


import org.aika.Iteration;
import org.aika.Model;
import org.aika.corpus.Document;
import org.aika.lattice.AndNode;
import org.aika.lattice.OrNode;
import org.aika.neuron.InputNeuron;
import org.aika.neuron.Neuron;
import org.aika.neuron.Synapse;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Lukas Molzberger
 */
public class CountingTest {


    @Test
    public void testActivationCounting() {
        Model m = new Model();
        AndNode.minFrequency = 0;

        Document doc = Document.create("aaaaaaaaaa");
        Iteration t = m.startIteration(doc, 0);

        InputNeuron inA = t.createOrLookupInputSignal("inA");
        Neuron outA = t.createAndNeuron(new Neuron("nA"), 0.5,
                new Iteration.Input()
                        .setMinInput(0.95)
                        .setWeight(100.0)
                        .setNeuron(inA)
                        .setStartVisibility(Synapse.RangeVisibility.MAX_OUTPUT)
                        .setEndVisibility(Synapse.RangeVisibility.MAX_OUTPUT)
                        .setMatchRange(true)
        );


        inA.addInput(t, 0, 1);
        inA.addInput(t, 0, 1);
        inA.addInput(t, 2, 3);
        inA.addInput(t, 3, 4);
        inA.addInput(t, 3, 4);
        inA.addInput(t, 5, 6);
        inA.addInput(t, 6, 7);
        inA.addInput(t, 7, 8);

        t.process();
        t.train();
        Assert.assertEquals(6.0, ((OrNode)outA.node).parents.first().node.frequency, 0.001);
    }
}
