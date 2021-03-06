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
package org.aika.corpus;


import org.aika.Activation;
import org.aika.Activation.Rounds;
import org.aika.Iteration;
import org.aika.corpus.Conflicts.Conflict;
import org.aika.neuron.Neuron.NormWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * The <code>ExpandNode</code> class represents a node in the search tree that is used to find the optimal
 * interpretation for a given document.
 */
public class ExpandNode implements Comparable<ExpandNode> {

    private static final Logger log = LoggerFactory.getLogger(ExpandNode.class);

    /**
     * This optimization may miss some cases and will not always return the best interpretation.
     */
    public static boolean INCOMPLETE_OPTIMIZATION = false;

    public static int MAX_SEARCH_STEPS = 10000;

    public int id;

    ExpandNode excludedParent;
    ExpandNode selectedParent;
    ExpandNode parent;

    long visited;
    Option refinement;
    NormWeight weightDelta = NormWeight.ZERO_WEIGHT;

    public List<StateChange> modifiedActs = new ArrayList<>();

    public TreeSet<ExpandNode> candidates = new TreeSet<>();


    public ExpandNode(ExpandNode parent) {
        this.parent = parent;
    }


    private void collectResults(Collection<Option> results) {
        results.add(refinement);
        if(selectedParent != null) selectedParent.collectResults(results);
    }


    // TODO: mark also selected nodes that consist of several expand nodes.
    private void markSelected(long v) {
        refinement.markSelected(v);
        if(selectedParent != null) selectedParent.markSelected(v);
    }


    public static ExpandNode createInitialExpandNode(Document doc) {
        List<Option> changed = new ArrayList<>();
        changed.add(doc.bottom);
        return createCandidate(doc, changed, null, null, null, doc.bottom);
    }


    public void computeSelectedOption(Iteration t) {
        Document doc = t.doc;

        ArrayList<Option> results = new ArrayList<>();
        results.add(doc.bottom);

        try {
            doc.selectedExpandNode = null;
            int[] searchSteps = new int[1];

            List<Option> rootRefs = expandRootRefinement(doc);
            refinement = expandRefinement(Option.add(doc, false, rootRefs.toArray(new Option[rootRefs.size()])));

            markCovered(null, visited, refinement);
            markExcluded(null, visited, refinement);

            weightDelta = t.vQueue.adjustWeight(this, rootRefs);

            if(Iteration.OPTIMIZE_DEBUG_OUTPUT) {
                log.info("Root ExpandNode:" + toString());
            }

            doc.selectedExpandNode = doc.root;
            doc.selectedMark = Option.visitedCounter++;
            markSelected(doc.selectedMark);

            doc.bottom.storeFinalWeight(Option.visitedCounter++);


            generateInitialCandidates(t);

            ExpandNode child = doc.root.selectCandidate();

            if(child != null) {
                child.search(t, doc.root, null, searchSteps);
            }

            if (doc.selectedExpandNode != null) {
                doc.selectedExpandNode.reconstructSelectedResult(t);
                doc.selectedExpandNode.collectResults(results);

                log.info("Selected ExandNode ID: " + doc.selectedExpandNode.id);
            }
        } catch(ExpandNodeException e) {
            System.err.println("Too many search steps!");
        }

        doc.selectedOption = Option.add(doc, true, results.toArray(new Option[results.size()]));
    }


    private void reconstructSelectedResult(Iteration t) {
        if(selectedParent != null) selectedParent.reconstructSelectedResult(t);

        changeState(StateChange.Mode.NEW);

        for(StateChange sc : modifiedActs) {
            Activation act = sc.act;
            if(act.finalState != null && act.finalState.value > 0.0) {
                t.activatedNeurons.add(act.key.n.neuron);
            }
        }
    }


    private void search(Iteration t, ExpandNode selectedParent, ExpandNode excludedParent, int[] searchSteps) {
        Document doc = t.doc;
        if(searchSteps[0] > MAX_SEARCH_STEPS) {
            throw new ExpandNodeException();
        }
        searchSteps[0]++;

        markCovered(null, visited, refinement);
        markExcluded(null, visited, refinement);

        if(Iteration.OPTIMIZE_DEBUG_OUTPUT) {
            log.info("Search Step: " + id);
            log.info(toString());
        }

        boolean f = doc.selectedMark == -1 || refinement.markedSelected != doc.selectedMark;

        changeState(StateChange.Mode.NEW);

        if(Iteration.OPTIMIZE_DEBUG_OUTPUT) {
            log.info(t.networkStateToString(true, true) + "\n");
        }

        double accNW = computeAccumulatedWeight().getNormWeight();
        double selectedAccNW = doc.selectedExpandNode != null ? doc.selectedExpandNode.computeAccumulatedWeight().getNormWeight() : 0.0;

        generateNextLevelCandidates(t, selectedParent, excludedParent);

        if(candidates.size() == 0 && accNW > selectedAccNW) {
            doc.selectedExpandNode = this;
            doc.selectedMark = Option.visitedCounter++;
            markSelected(doc.selectedMark);

            doc.bottom.storeFinalWeight(Option.visitedCounter++);
        }

        ExpandNode child = selectCandidate();
        if(child != null) {
            child.search(t, this, excludedParent, searchSteps);
        }
        changeState(StateChange.Mode.OLD);

        if(f || !INCOMPLETE_OPTIMIZATION) {
            child = selectedParent.selectCandidate();
            if(child != null) {
                child.search(t, selectedParent, this, searchSteps);
            }
        }
    }


    private ExpandNode selectCandidate() {
        if(candidates.isEmpty()) return null;
        return candidates.pollFirst();
    }


    public void generateInitialCandidates(Iteration t) {
        candidates = new TreeSet<>();
        for(Option cn: collectConflicts(t.doc)) {
            List<Option> changed = new ArrayList<>();
            ExpandNode c = createCandidate(t.doc, changed, this, this, null, cn);

            c.weightDelta = t.vQueue.adjustWeight(c, changed);
            if(Iteration.OPTIMIZE_DEBUG_OUTPUT) {
                log.info("Search Step: " + c.id + "  Candidate Weight Delta: " + c.weightDelta);
                log.info(t.networkStateToString(true, true) + "\n");
            }

            c.changeState(StateChange.Mode.OLD);

            candidates.add(c);
        }
    }


    public void generateNextLevelCandidates(Iteration t, ExpandNode selectedParent, ExpandNode excludedParent) {
        candidates = new TreeSet<>();

        for(ExpandNode pc: selectedParent.candidates) {
            if(!isCovered(pc.refinement.markedCovered) && !checkExcluded(pc.refinement, Option.visitedCounter++) && !pc.refinement.contains(refinement, false)) {
                List<Option> changed = new ArrayList<>();
                ExpandNode c = createCandidate(t.doc, changed, this, this, excludedParent, pc.refinement);

                c.weightDelta = t.vQueue.adjustWeight(c, changed);
                c.changeState(StateChange.Mode.OLD);

                candidates.add(c);
            }
        }
    }


    private boolean checkExcluded(Option ref, long v) {
        if(ref.visitedCheckExcluded == v) return false;
        ref.visitedCheckExcluded = v;

        if(isCovered(ref.markedExcluded)) return true;

        for(Option pn: ref.parents) {
            if(checkExcluded(pn, v)) return true;
        }

        return false;
    }


    public static List<Option> collectConflicts(Document doc) {
        List<Option> results = new ArrayList<>();
        long v = Option.visitedCounter++;
        for(Option n: doc.bottom.children) {
            for(Conflict c: n.conflicts.primary.values()) {
                if(c.secondary.visitedCollectConflicts != v) {
                    c.secondary.visitedCollectConflicts = v;
                    results.add(c.secondary);
                }
            }
        }
        return results;
    }


    private static List<Option> expandRootRefinement(Document doc) {
        ArrayList<Option> tmp = new ArrayList<>();
        tmp.add(doc.bottom);
        for(Option pn: doc.bottom.children) {
            if((pn.orOptions == null || pn.orOptions.isEmpty()) && pn.conflicts.primary.isEmpty() && pn.conflicts.secondary.isEmpty()) {
                tmp.add(pn);
            }
        }
        return tmp;
    }


    private Option expandRefinement(Option ref) {
        ArrayList<Option> tmp = new ArrayList<>();
        tmp.add(ref);
        expandRefinementRecursiveStep(tmp, ref, Option.visitedCounter++);
        Option expRef = Option.add(ref.doc, false, tmp.toArray(new Option[tmp.size()]));

        if(ref == expRef) return ref;
        else return expandRefinement(expRef);
    }


    private void expandRefinementRecursiveStep(Collection<Option> results, Option n, long v) {
        if(n.visitedExpandRefinementRecursiveStep == v) return;
        n.visitedExpandRefinementRecursiveStep = v;

        if (n.refByOrOption != null) {
            for (Option on : n.refByOrOption) {
                if(!on.conflicts.hasConflicts()) {
                    results.add(on);
                }
            }
        }
        for(Option pn: n.parents) {
            expandRefinementRecursiveStep(results, pn, v);
        }

        if(n.isBottom()) return;

        // Expand options that are partially covered by this refinement and partially by an earlier expand node.
        for(Option cn: n.children) {
            if(cn.visitedExpandRefinementRecursiveStep == v) break;

            // Check if all parents are either contained in this refinement or an earlier refinement.
            boolean covered = true;
            for(Option cnp: cn.parents) {
                if(cnp.visitedExpandRefinementRecursiveStep != v && !isCovered(cnp.markedCovered)) {
                    covered = false;
                    break;
                }
            }

            if(covered) {
                expandRefinementRecursiveStep(results, cn, v);
            }
        }
    }


    public NormWeight computeAccumulatedWeight() {
        return (selectedParent != null ? weightDelta.add(selectedParent.computeAccumulatedWeight()) : weightDelta);
    }


    public boolean isCovered(long g) {
        return g == visited || (selectedParent != null && selectedParent.isCovered(g));
    }


    private boolean markCovered(List<Option> changed, long v, Option n) {
        if(n.visitedMarkCovered == v) return false;
        n.visitedMarkCovered = v;

        if(isCovered(n.markedCovered)) return false;

        n.markedCovered = v;

        if(n.isBottom()) {
            return false;
        }

        for(Option p: n.parents) {
            if(markCovered(changed, v, p)) return true;
        }

        for(Option c: n.children) {
            if(c.visitedMarkCovered == v) continue;

            if(!containedInSelectedBranch(v, c)) continue;

            if(c.isConflicting(Option.visitedCounter++)) return true;

            c.markedCovered = v;

            if(markCovered(changed, v, c)) return true;
        }

        if(changed != null) changed.add(n);

        return false;
    }


    private void markExcluded(List<Option> changed, long v, Option n) {
        List<Option> conflicting = new ArrayList<>();
        Conflicts.collectAllConflicting(conflicting, n, Option.visitedCounter++);
        for(Option cn: conflicting) {
            markExcludedRecursiveStep(changed, v, cn);
        }
    }


    private void markExcludedRecursiveStep(List<Option> changed, long v, Option n) {
        if(n.markedExcluded == v || isCovered(n.markedExcluded)) return;
        n.markedExcluded = v;

        for(Option c: n.children) {
            markExcludedRecursiveStep(changed, v, c);
        }

        // If the or option has two input options and one of them is already excluded, then when the other one is excluded we also have to exclude the or option.
        if(n.linkedByLCS != null) {
            for(Option c: n.linkedByLCS) {
                markExcludedRecursiveStep(changed, v, c);
            }
        }

        if(changed != null) changed.add(n);

        return;
    }


    public boolean containedInSelectedBranch(long v, Option n) {
        for(Option p: n.parents) {
            if(p.markedCovered != v && !isCovered(p.markedCovered)) return false;
        }
        return true;
    }


    public static class ExpandNodeException extends RuntimeException {

        private static final long serialVersionUID = -9084467053069262753L;

    }


    public String toString() {
        return (selectedParent != null ? selectedParent.toString() : "") + "->" + refinement.toString() + ":" + computeAccumulatedWeight();
    }


    public static ExpandNode createCandidate(Document doc, List<Option> changed, ExpandNode parent, ExpandNode selectedParent, ExpandNode excludedParent, Option ref) {
        ExpandNode cand = new ExpandNode(parent);
        cand.id = doc.expandNodeIdCounter++;
        cand.visited = Option.visitedCounter++;
        cand.selectedParent = selectedParent;
        cand.excludedParent = excludedParent;
        cand.refinement = cand.expandRefinement(ref);
        cand.markCovered(changed, cand.visited, cand.refinement);
        cand.markExcluded(changed, cand.visited, cand.refinement);

        if(Iteration.OPTIMIZE_DEBUG_OUTPUT) {
            log.info("\n \n Generate Candidate: " + cand.refinement.toString());
        }

        return cand;
    }


    public void changeState(StateChange.Mode m) {
        for(StateChange sc: modifiedActs) {
            sc.restoreState(m);
        }
    }


    @Override
    public int compareTo(ExpandNode c) {
        int r = Double.compare(c.computeAccumulatedWeight().getNormWeight(), computeAccumulatedWeight().getNormWeight());
        if(r != 0) return r;
        return refinement.compareTo(c.refinement);
    }


    public static class StateChange {
        public Activation act;

        public Rounds oldRounds;
        public Rounds newRounds;

        public enum Mode { OLD, NEW }

        public static void saveOldState(List<StateChange> changes, Activation act, long v) {
            StateChange sc = act.currentStateChange;
            if(sc == null || act.currentStateV != v) {
                sc = new StateChange();
                sc.oldRounds = act.rounds.copy();
                act.currentStateChange = sc;
                act.currentStateV = v;
                sc.act = act;
                if(changes != null) {
                    changes.add(sc);
                }
            }
        }

        public static void saveNewState(Activation act) {
            StateChange sc = act.currentStateChange;

            sc.newRounds = act.rounds;
        }

        public void restoreState(Mode m) {
            act.rounds = (m == Mode.OLD ? oldRounds : newRounds).copy();
        }
    }
}
