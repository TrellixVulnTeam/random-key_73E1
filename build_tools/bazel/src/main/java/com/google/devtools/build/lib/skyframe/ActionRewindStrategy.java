// Copyright 2018 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.skyframe;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Comparators.greatest;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.flogger.GoogleLogger;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.ActionExecutionException;
import com.google.devtools.build.lib.actions.ActionInput;
import com.google.devtools.build.lib.actions.ActionInputDepOwners;
import com.google.devtools.build.lib.actions.ActionLookupData;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.LostInputsActionExecutionException;
import com.google.devtools.build.lib.bugreport.BugReport;
import com.google.devtools.build.lib.events.ExtendedEventHandler;
import com.google.devtools.build.lib.skyframe.proto.ActionRewind.ActionRewindEvent;
import com.google.devtools.build.skyframe.SkyFunction.Environment;
import com.google.devtools.build.skyframe.SkyFunction.Restart;
import com.google.devtools.build.skyframe.SkyKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;

/**
 * Given an action that failed to execute because of lost inputs which were generated by other
 * actions, this finds the actions which generated them and the set of Skyframe nodes which must be
 * restarted in order to recreate the lost inputs.
 */
public class ActionRewindStrategy {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  @VisibleForTesting public static final int MAX_REPEATED_LOST_INPUTS = 20;
  @VisibleForTesting public static final int MAX_ACTION_REWIND_EVENTS = 5;
  private static final int MAX_LOST_INPUTS_RECORDED = 5;

  // Note that these references are mutated only outside of Skyframe evaluations, and accessed only
  // inside of them. Their visibility piggybacks on Skyframe evaluation synchronizations, like
  // ActionExecutionFunction's stateMap does.
  private ConcurrentHashMultiset<LostInputRecord> lostInputRecords =
      ConcurrentHashMultiset.create();
  private ConcurrentLinkedQueue<RewindPlanStats> rewindPlansStats = new ConcurrentLinkedQueue<>();

  /**
   * Returns a {@link RewindPlan} specifying:
   *
   * <ol>
   *   <li>the Skyframe nodes to restart to recreate the lost inputs specified by {@code
   *       lostInputsException}
   *   <li>the actions whose execution state (in {@link SkyframeActionExecutor}) must be reset
   *       (aside from failedAction, which the caller already knows must be reset)
   * </ol>
   *
   * <p>Note that all Skyframe nodes between the currently executing (failed) action's node and the
   * nodes corresponding to the actions which create the lost inputs, inclusive, must be restarted.
   * This ensures that reevaluating the current node will also reevaluate the nodes that will
   * recreate the lost inputs.
   *
   * @throws ActionExecutionException if any lost inputs have been seen by this action as lost
   *     before, or if any lost inputs are not the outputs of previously executed actions
   */
  RewindPlan getRewindPlan(
      Action failedAction,
      ActionLookupData actionLookupData,
      Iterable<? extends SkyKey> failedActionDeps,
      LostInputsActionExecutionException lostInputsException,
      ActionInputDepOwners inputDepOwners,
      Environment env)
      throws ActionExecutionException, InterruptedException {
    ImmutableList<LostInputRecord> lostInputRecordsThisAction =
        checkIfActionLostInputTooManyTimes(actionLookupData, failedAction, lostInputsException);

    ImmutableList<ActionInput> lostInputs = lostInputsException.getLostInputs().values().asList();

    // This graph tracks which Skyframe nodes must be restarted and the dependency relationships
    // between them.
    MutableGraph<SkyKey> rewindGraph = GraphBuilder.directed().allowsSelfLoops(false).build();
    rewindGraph.addNode(actionLookupData);

    // SkyframeActionExecutor must re-execute the actions being restarted, so we must tell it to
    // evict its cached results for those actions. This collection tracks those actions (aside from
    // failedAction, which the caller of getRewindPlan already knows must be restarted).
    ImmutableList.Builder<Action> additionalActionsToRestart = ImmutableList.builder();

    Set<Artifact.DerivedArtifact> lostArtifacts =
        getLostInputOwningDirectDeps(
            lostInputs,
            inputDepOwners,
            ImmutableSet.copyOf(failedActionDeps),
            failedAction,
            lostInputsException);

    for (Artifact.DerivedArtifact lostArtifact : lostArtifacts) {
      SkyKey artifactKey = Artifact.key(lostArtifact);

      Map<ActionLookupData, Action> actionMap = getActionsForLostArtifact(lostArtifact, env);
      if (actionMap == null) {
        // Some deps of the artifact are not done. Another rewind must be in-flight, and there is no
        // need to restart the shared deps twice.
        continue;
      }
      ImmutableList<ActionAndLookupData> newlyVisitedActions =
          addArtifactDepsAndGetNewlyVisitedActions(rewindGraph, lostArtifact, actionMap);
      // Note that artifactKey must be restarted. We do this after
      // addArtifactDepsAndGetNewlyVisitedActions so that it can track if actions are already known
      // to be in the graph.
      rewindGraph.putEdge(actionLookupData, artifactKey);
      additionalActionsToRestart.addAll(actions(newlyVisitedActions));
      checkActions(newlyVisitedActions, env, rewindGraph, additionalActionsToRestart);
    }

    int lostInputRecordsCount = lostInputRecordsThisAction.size();
    rewindPlansStats.add(
        RewindPlanStats.create(
            failedAction,
            rewindGraph.nodes().size(),
            lostInputRecordsCount,
            lostInputRecordsThisAction.subList(
                0, Math.min(lostInputRecordsCount, MAX_LOST_INPUTS_RECORDED))));
    return new RewindPlan(
        Restart.selfAnd(ImmutableGraph.copyOf(rewindGraph)), additionalActionsToRestart.build());
  }

  /**
   * Log the top N action rewind events and clear the history of failed actions' lost inputs and
   * rewind plans.
   */
  void reset(ExtendedEventHandler eventHandler) {
    ImmutableList<ActionRewindEvent> topActionRewindEvents =
        rewindPlansStats.stream()
            .collect(
                greatest(
                    MAX_ACTION_REWIND_EVENTS, comparing(RewindPlanStats::invalidatedNodesCount)))
            .stream()
            .map(ActionRewindingStats::toActionRewindEventProto)
            .collect(toImmutableList());
    ActionRewindingStats rewindingStats =
        new ActionRewindingStats(lostInputRecords.size(), topActionRewindEvents);
    eventHandler.post(rewindingStats);
    lostInputRecords = ConcurrentHashMultiset.create();
    rewindPlansStats = new ConcurrentLinkedQueue<>();
  }

  /** Returns all lost input records that will cause the failed action to rewind. */
  private ImmutableList<LostInputRecord> checkIfActionLostInputTooManyTimes(
      ActionLookupData actionLookupData,
      Action failedAction,
      LostInputsActionExecutionException lostInputsException)
      throws ActionExecutionException {
    ImmutableMap<String, ActionInput> lostInputsByDigest = lostInputsException.getLostInputs();
    ImmutableList.Builder<LostInputRecord> lostInputRecordsThisAction = ImmutableList.builder();
    for (Map.Entry<String, ActionInput> entry : lostInputsByDigest.entrySet()) {
      // The same action losing the same input more than once is unexpected [*]. The action should
      // have waited until the depended-on action which generates the lost input is (re)run before
      // trying again.
      //
      // Note that we could enforce a stronger check: if action A, which depends on an input N
      // previously detected as lost (by any action, not just A), discovers that N is still lost,
      // and action A started after the re-evaluation of N's generating action, then something has
      // gone wrong. Administering that check would be more complex (e.g., the start/completion
      // times of actions would need tracking), so we punt on it for now.
      //
      // [*], TODO(b/123993876): To mitigate a race condition (believed to be) caused by
      // non-topological Skyframe dirtying of depended-on nodes, this check fails the build only if
      // the same input is repeatedly lost.
      String digest = entry.getKey();
      LostInputRecord lostInputRecord =
          LostInputRecord.create(actionLookupData, digest, entry.getValue().getExecPathString());
      lostInputRecordsThisAction.add(lostInputRecord);
      int priorLosses = lostInputRecords.add(lostInputRecord, /*occurrences=*/ 1);
      if (MAX_REPEATED_LOST_INPUTS <= priorLosses) {
        BugReport.sendBugReport(
            new IllegalStateException(
                String.format(
                    "lost input too many times (#%s) for the same action. lostInput: %s, "
                        + "lostInput digest: %s, failedAction: %.10000s",
                    priorLosses + 1, lostInputsByDigest.get(digest), digest, failedAction)));
        throw new ActionExecutionException(
            lostInputsException, failedAction, /*catastrophe=*/ false);
      } else if (0 < priorLosses) {
        logger.atInfo().log(
            "lost input again (#%s) for the same action. lostInput: %s, "
                + "lostInput digest: %s, failedAction: %.10000s",
            priorLosses + 1, lostInputsByDigest.get(digest), digest, failedAction);
      }
    }
    return lostInputRecordsThisAction.build();
  }

  private static Set<Artifact.DerivedArtifact> getLostInputOwningDirectDeps(
      ImmutableList<ActionInput> lostInputs,
      ActionInputDepOwners inputDepOwners,
      ImmutableSet<SkyKey> failedActionDeps,
      Action failedAction,
      LostInputsActionExecutionException lostInputsException)
      throws ActionExecutionException {

    Set<Artifact.DerivedArtifact> lostInputOwningDirectDeps = new HashSet<>();
    for (ActionInput lostInput : lostInputs) {
      boolean foundLostInputDepOwner = false;

      Collection<Artifact> owners = inputDepOwners.getDepOwners(lostInput);
      for (Artifact owner : owners) {
        checkDerived(/*lostInputQualifier=*/ " owner", owner, failedAction, lostInputsException);

        // Rewinding must invalidate all Skyframe paths from the failed action to the action which
        // generates the lost input. Intermediate nodes not on the shortest path to that action may
        // have values that depend on the output of that action. If these intermediate nodes are not
        // invalidated, then their values may become stale. Therefore, this method collects not only
        // the first action dep associated with the lost input, but all of them.

        Collection<Artifact> transitiveOwners = inputDepOwners.getDepOwners(owner);
        for (Artifact transitiveOwner : transitiveOwners) {
          checkDerived(
              /*lostInputQualifier=*/ " transitive owner",
              transitiveOwner,
              failedAction,
              lostInputsException);

          if (failedActionDeps.contains(Artifact.key(transitiveOwner))) {
            // The lost input is included in an aggregation artifact (e.g. a tree artifact or
            // fileset) that is included by an aggregation artifact (e.g. a middleman) that the
            // action directly depends on.
            lostInputOwningDirectDeps.add((Artifact.DerivedArtifact) transitiveOwner);
            foundLostInputDepOwner = true;
          }
        }

        if (failedActionDeps.contains(Artifact.key(owner))) {
          // The lost input is included in an aggregation artifact (e.g. a tree artifact, fileset,
          // or middleman) that the action directly depends on.
          lostInputOwningDirectDeps.add((Artifact.DerivedArtifact) owner);
          foundLostInputDepOwner = true;
        }
      }

      if (lostInput instanceof Artifact
          && failedActionDeps.contains(Artifact.key((Artifact) lostInput))) {
        checkDerived(
            /*lostInputQualifier=*/ "", (Artifact) lostInput, failedAction, lostInputsException);

        lostInputOwningDirectDeps.add((Artifact.DerivedArtifact) lostInput);
        foundLostInputDepOwner = true;
      }

      if (foundLostInputDepOwner) {
        continue;
      }

      // Rewinding can't do anything about a lost input that can't be associated with a direct dep
      // of the failed action. This may happen if the action consists of a sequence of spawns where
      // an output generated by one spawn is consumed by another but was lost in-between. In this
      // case, reevaluating the failed action (and no other deps) may help, because doing so may
      // rerun the generating spawn.
      //
      // In other cases, such as with bugs, when the action fails enough it will cause a crash in
      // checkIfActionLostInputTooManyTimes. We log that this has occurred.
      logger.atWarning().log(
          "lostInput not a dep of the failed action, and can't be associated with such a dep. "
              + "lostInput: %s, owners: %s, failedAction: %.10000s",
          lostInput, owners, failedAction);
    }
    return lostInputOwningDirectDeps;
  }

  private static void checkDerived(
      String lostInputQualifier,
      Artifact expectedDerived,
      Action failedAction,
      LostInputsActionExecutionException lostInputsException)
      throws ActionExecutionException {
    if (!expectedDerived.isSourceArtifact()) {
      return;
    }
    // TODO(b/19539699): tighten signatures for ActionInputDepOwnerMap to make this impossible.
    BugReport.sendBugReport(
        new IllegalStateException(
            String.format(
                "Unexpected source artifact as lost input%s: %s %s",
                lostInputQualifier, expectedDerived, failedAction)));
    throw new ActionExecutionException(lostInputsException, failedAction, /*catastrophe=*/ false);
  }

  /**
   * Looks at each action in {@code actionsToCheck} and determines whether additional artifacts,
   * actions, and (in the case of {@link SkyframeAwareAction}s) other Skyframe nodes need to be
   * restarted. If this finds more actions to restart, those actions are recursively checked too.
   */
  private void checkActions(
      ImmutableList<ActionAndLookupData> actionsToCheck,
      Environment env,
      MutableGraph<SkyKey> rewindGraph,
      ImmutableList.Builder<Action> additionalActionsToRestart)
      throws InterruptedException {

    ArrayDeque<ActionAndLookupData> uncheckedActions = new ArrayDeque<>(actionsToCheck);
    while (!uncheckedActions.isEmpty()) {
      ActionAndLookupData actionAndLookupData = uncheckedActions.removeFirst();
      ActionLookupData actionKey = actionAndLookupData.lookupData();
      Action action = actionAndLookupData.action();
      ArrayList<Artifact.DerivedArtifact> artifactsToCheck = new ArrayList<>();
      ArrayList<ActionLookupData> newlyDiscoveredActions = new ArrayList<>();

      if (action instanceof SkyframeAwareAction) {
        // This action depends on more than just its input artifact values. We need to also restart
        // the Skyframe subgraph it depends on, up to and including any artifacts, which may
        // aggregate multiple actions.
        addSkyframeAwareDepsAndGetNewlyVisitedArtifactsAndActions(
            rewindGraph,
            actionKey,
            (SkyframeAwareAction) action,
            artifactsToCheck,
            newlyDiscoveredActions);
      }

      if (action.mayInsensitivelyPropagateInputs()) {
        // Restarting this action won't recreate the missing input. We need to also restart this
        // action's non-source inputs and the actions which created those inputs.
        addPropagatingActionDepsAndGetNewlyVisitedArtifactsAndActions(
            rewindGraph, actionKey, action, artifactsToCheck, newlyDiscoveredActions);
      }

      for (ActionLookupData actionLookupData : newlyDiscoveredActions) {
        Action additionalAction =
            checkNotNull(
                ActionUtils.getActionForLookupData(env, actionLookupData), actionLookupData);
        additionalActionsToRestart.add(additionalAction);
        uncheckedActions.add(ActionAndLookupData.create(actionLookupData, additionalAction));
      }
      for (Artifact.DerivedArtifact artifact : artifactsToCheck) {
        Map<ActionLookupData, Action> actionMap = getActionsForLostArtifact(artifact, env);
        if (actionMap == null) {
          continue;
        }
        ImmutableList<ActionAndLookupData> newlyVisitedActions =
            addArtifactDepsAndGetNewlyVisitedActions(rewindGraph, artifact, actionMap);
        additionalActionsToRestart.addAll(actions(newlyVisitedActions));
        uncheckedActions.addAll(newlyVisitedActions);
      }
    }
  }

  /**
   * For a {@link SkyframeAwareAction} {@code action} with key {@code actionKey}, add its Skyframe
   * subgraph to {@code rewindGraph}, any {@link Artifact}s to {@code newlyVisitedArtifacts}, and
   * any {@link ActionLookupData}s to {@code newlyVisitedActions}.
   */
  private static void addSkyframeAwareDepsAndGetNewlyVisitedArtifactsAndActions(
      MutableGraph<SkyKey> rewindGraph,
      ActionLookupData actionKey,
      SkyframeAwareAction<?> action,
      ArrayList<Artifact.DerivedArtifact> newlyVisitedArtifacts,
      ArrayList<ActionLookupData> newlyVisitedActions) {

    ImmutableGraph<SkyKey> graph = action.getSkyframeDependenciesForRewinding(actionKey);
    if (graph.nodes().isEmpty()) {
      return;
    }
    assertSkyframeAwareRewindingGraph(graph, actionKey);

    Set<EndpointPair<SkyKey>> edges = graph.edges();
    for (EndpointPair<SkyKey> edge : edges) {
      SkyKey target = edge.target();
      if (target instanceof Artifact && rewindGraph.addNode(target)) {
        newlyVisitedArtifacts.add(((Artifact.DerivedArtifact) target));
      }
      if (target instanceof ActionLookupData && rewindGraph.addNode(target)) {
        newlyVisitedActions.add(((ActionLookupData) target));
      }
      rewindGraph.putEdge(edge.source(), edge.target());
    }
  }

  /**
   * For a propagating {@code action} with key {@code actionKey}, add its generated inputs' keys to
   * {@code rewindGraph}, add edges from {@code actionKey} to those keys, add any {@link Artifact}s
   * to {@code newlyVisitedArtifacts}, and add any {@link ActionLookupData}s to {@code
   * newlyVisitedActions}.
   */
  private static void addPropagatingActionDepsAndGetNewlyVisitedArtifactsAndActions(
      MutableGraph<SkyKey> rewindGraph,
      ActionLookupData actionKey,
      Action action,
      ArrayList<Artifact.DerivedArtifact> newlyVisitedArtifacts,
      ArrayList<ActionLookupData> newlyVisitedActions) {

    for (Artifact input : action.getInputs().toList()) {
      if (input.isSourceArtifact()) {
        continue;
      }
      SkyKey artifactKey = Artifact.key(input);
      // Restarting all derived inputs of propagating actions is overkill. Preferably, we'd want
      // to only restart the inputs which correspond to the known lost outputs. The information
      // to do this is probably present in the data available to #getRewindPlan.
      //
      // Rewinding is expected to be rare, so refining this may not be necessary.
      boolean newlyVisited = rewindGraph.addNode(artifactKey);
      if (newlyVisited) {
        if (artifactKey instanceof Artifact) {
          newlyVisitedArtifacts.add((Artifact.DerivedArtifact) artifactKey);
        } else if (artifactKey instanceof ActionLookupData) {
          newlyVisitedActions.add((ActionLookupData) artifactKey);
        }
      }
      rewindGraph.putEdge(actionKey, artifactKey);
    }

    // Rewinding ignores artifacts returned by Action#getAllowedDerivedInputs because:
    // 1) the set of actions with non-throwing implementations of getAllowedDerivedInputs,
    // 2) the set of actions that "mayInsensitivelyPropagateInputs",
    // should have no overlap. Log a bug report if we see such an action:
    if (action.discoversInputs()) {
      BugReport.sendBugReport(
          new IllegalStateException(
              String.format(
                  "Action insensitively propagates and discovers inputs. actionKey: %s, action: "
                      + "%.10000s",
                  actionKey, action)));
    }
  }

  /**
   * For an artifact {@code artifact} with generating actions (and their associated {@link
   * ActionLookupData}) {@code actionMap}, add those actions' keys to {@code rewindGraph} and add
   * edges from {@code artifact} to those keys.
   *
   * <p>Returns a list of key+action pairs for each action whose key was newly added to the graph.
   */
  private ImmutableList<ActionAndLookupData> addArtifactDepsAndGetNewlyVisitedActions(
      MutableGraph<SkyKey> rewindGraph,
      Artifact artifact,
      Map<ActionLookupData, Action> actionMap) {

    ImmutableList.Builder<ActionAndLookupData> newlyVisitedActions =
        ImmutableList.builderWithExpectedSize(actionMap.size());
    SkyKey artifactKey = Artifact.key(artifact);
    for (Map.Entry<ActionLookupData, Action> actionEntry : actionMap.entrySet()) {
      ActionLookupData actionKey = actionEntry.getKey();
      if (rewindGraph.addNode(actionKey)) {
        newlyVisitedActions.add(ActionAndLookupData.create(actionKey, actionEntry.getValue()));
      }
      if (!artifactKey.equals(actionKey)) {
        rewindGraph.putEdge(artifactKey, actionKey);
      }
    }
    return newlyVisitedActions.build();
  }

  /**
   * Returns the map of {@code lostInput}'s execution-phase dependencies (i.e. generating actions),
   * keyed by their {@link ActionLookupData} keys, or {@code null} if any of those dependencies are
   * not done.
   */
  @Nullable
  private Map<ActionLookupData, Action> getActionsForLostArtifact(
      Artifact.DerivedArtifact lostInput, Environment env) throws InterruptedException {
    Set<ActionLookupData> actionExecutionDeps = getActionExecutionDeps(lostInput, env);
    if (actionExecutionDeps == null) {
      return null;
    }

    Map<ActionLookupData, Action> actions =
        Maps.newHashMapWithExpectedSize(actionExecutionDeps.size());
    for (ActionLookupData dep : actionExecutionDeps) {
      actions.put(dep, checkNotNull(ActionUtils.getActionForLookupData(env, dep)));
    }
    return actions;
  }

  /**
   * Returns the set of {@code lostInput}'s execution-phase dependencies (i.e. generating actions),
   * or {@code null} if any of those dependencies are not done.
   */
  @Nullable
  private Set<ActionLookupData> getActionExecutionDeps(
      Artifact.DerivedArtifact lostInput, Environment env) throws InterruptedException {
    if (!lostInput.isTreeArtifact()) {
      return ImmutableSet.of(lostInput.getGeneratingActionKey());
    }
    ArtifactFunction.ArtifactDependencies artifactDependencies =
        ArtifactFunction.ArtifactDependencies.discoverDependencies(lostInput, env);
    if (artifactDependencies == null) {
      return null;
    }

    if (!artifactDependencies.isTemplateActionForTreeArtifact()) {
      return ImmutableSet.of(lostInput.getGeneratingActionKey());
    }
    ArtifactFunction.ActionTemplateExpansion actionTemplateExpansion =
        artifactDependencies.getActionTemplateExpansion(env);
    if (actionTemplateExpansion == null) {
      return null;
    }
    // This ignores the ActionTemplateExpansionKey dependency of the template artifact because we
    // expect to never need to rewind that.
    return ImmutableSet.copyOf(actionTemplateExpansion.getExpandedActionExecutionKeys());
  }

  private static void assertSkyframeAwareRewindingGraph(
      ImmutableGraph<SkyKey> graph, ActionLookupData actionKey) {
    checkArgument(
        graph.isDirected(),
        "SkyframeAwareAction's rewinding graph is undirected. graph: %s, actionKey: %s",
        graph,
        actionKey);
    checkArgument(
        !graph.allowsSelfLoops(),
        "SkyframeAwareAction's rewinding graph allows self loops. graph: %s, actionKey: %s",
        graph,
        actionKey);
    checkArgument(
        graph.nodes().contains(actionKey),
        "SkyframeAwareAction's rewinding graph does not contain its action root. graph: %s, "
            + "actionKey: %s",
        graph,
        actionKey);
    checkArgument(
        Iterables.size(Traverser.forGraph(graph).breadthFirst(actionKey)) == graph.nodes().size(),
        "SkyframeAwareAction's rewinding graph has nodes unreachable from its action root. "
            + "graph: %s, actionKey: %s",
        graph,
        actionKey);

    for (EndpointPair<SkyKey> edge : graph.edges()) {
      SkyKey target = edge.target();
      checkArgument(
          !(target instanceof Artifact && ((Artifact) target).isSourceArtifact()),
          "SkyframeAwareAction's rewinding graph contains source artifact. graph: %s, "
              + "rootActionNode: %s, sourceArtifact: %s",
          graph,
          actionKey,
          target);
      checkState(
          !(target instanceof Artifact) || target instanceof Artifact.DerivedArtifact,
          "A non-source artifact must be derived. graph: %s, rootActionNode: %s, sourceArtifact:"
              + " %s",
          graph,
          actionKey,
          target);
    }
  }

  static class RewindPlan {
    private final Restart nodesToRestart;
    private final ImmutableList<Action> additionalActionsToRestart;

    RewindPlan(Restart nodesToRestart, ImmutableList<Action> additionalActionsToRestart) {
      this.nodesToRestart = nodesToRestart;
      this.additionalActionsToRestart = additionalActionsToRestart;
    }

    Restart getNodesToRestart() {
      return nodesToRestart;
    }

    ImmutableList<Action> getAdditionalActionsToRestart() {
      return additionalActionsToRestart;
    }
  }

  /**
   * A lite version of the RewindPlan that contains the metrics, failed action, and lost inputs.
   * This object will persist across the build, so it will be more memory efficient than saving the
   * entire rewind graph for each rewind plan.
   */
  @AutoValue
  abstract static class RewindPlanStats {

    abstract Action failedAction();

    abstract int invalidatedNodesCount();

    abstract int lostInputRecordsCount();

    abstract ImmutableList<LostInputRecord> sampleLostInputRecords();

    static RewindPlanStats create(
        Action failedAction,
        int invalidatedNodesCount,
        int lostInputRecordsCount,
        ImmutableList<LostInputRecord> sampleLostInputRecords) {
      return new AutoValue_ActionRewindStrategy_RewindPlanStats(
          failedAction, invalidatedNodesCount, lostInputRecordsCount, sampleLostInputRecords);
    }
  }

  /**
   * A record indicating that a Skyframe action execution failed because it lost an input with the
   * specified digest.
   */
  @AutoValue
  abstract static class LostInputRecord {

    abstract ActionLookupData failedActionLookupData();

    abstract String lostInputDigest();

    abstract String lostInputPath();

    static LostInputRecord create(
        ActionLookupData failedActionLookupData, String lostInputDigest, String lostInputPath) {
      return new AutoValue_ActionRewindStrategy_LostInputRecord(
          failedActionLookupData, lostInputDigest, lostInputPath);
    }
  }

  @AutoValue
  abstract static class ActionAndLookupData {

    abstract ActionLookupData lookupData();

    abstract Action action();

    static ActionAndLookupData create(ActionLookupData lookupData, Action action) {
      return new AutoValue_ActionRewindStrategy_ActionAndLookupData(lookupData, action);
    }
  }

  private static ImmutableList<Action> actions(List<ActionAndLookupData> newlyVisitedActions) {
    return newlyVisitedActions.stream().map(ActionAndLookupData::action).collect(toImmutableList());
  }
}