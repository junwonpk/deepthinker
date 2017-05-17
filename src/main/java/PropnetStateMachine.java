import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;


@SuppressWarnings("unused")
public class PropnetStateMachine extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            ordering = getOrdering();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    public void clearPropNet() {
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(false);
        }
    }

    public void markActions(MachineState state) {
    	Set<GdlSentence> contents = state.getContents();
    	Map<GdlSentence, Proposition> inputProps = propNet.getInputPropositions();
    	for (GdlSentence sen : inputProps.keySet()) {
    		if (contents.contains(sen)) {
        		inputProps.get(sen).setValue(true);
    		}
    	}
    }

    public void markBases(MachineState state) {
    	Set<GdlSentence> contents = state.getContents();
    	Map<GdlSentence, Proposition> baseProps = propNet.getBasePropositions();
    	for (GdlSentence sen : baseProps.keySet()) {
    		if (contents.contains(sen)) {
        		baseProps.get(sen).setValue(true);
    		}
    	}
    }

    public boolean conjunction (Component p) {
    	List<Component> inputs = new ArrayList<Component>(p.getInputs());
    	int len = inputs.size();
    	for (int i = 0; i < len; i++) {
    		if (!(propMarkP(inputs.get(i)))) {
    			return false;
    		}
    	}
    	return true;
    }

    public boolean disjunction (Component p) {
    	List<Component> inputs = new ArrayList<Component>(p.getInputs());
    	int len = inputs.size();
    	for (int i = 0; i < len; i++) {
    		if (propMarkP(inputs.get(i))) {
    			return true;
    		}
    	}
    	return false;
    }

    public boolean negation (Component p) {
    	return (!(propMarkP(p.getSingleInput())));
    }

    public boolean propMarkP(Component p) {
    	if (p.getInputs().size() == 0) {
    		// This is an input proposition
    		return p.getValue();
    	} else {
    		if (p instanceof And) {
    			// Conjunction
    			return conjunction(p);
    		} else if (p instanceof Or) {
    			return disjunction(p);
    		} else if (p instanceof Not) {
    			return negation(p);
    		}


    		List<Component> inputs = new ArrayList<Component>(p.getInputs());

    		if (p.getInputs().size() == 1 && inputs.get(0) instanceof Transition) {
    			// This is a base proposition
    			return p.getValue();
    		} else {
    			return propMarkP(p.getSingleInput());
    		}
    	}
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
    	markBases(state);
    	Proposition termProp = propNet.getTerminalProposition();
        return propMarkP(termProp);
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
    	Set<GdlSentence> contents = state.getContents();
        Map<Role, Set<Proposition>> goals = propNet.getGoalPropositions();
        Set<Proposition> myGoals = goals.get(role);
        System.out.println(myGoals.size());
        for (Proposition goal: myGoals) {
        	return getGoalValue(goal);
        }
        return -1;
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {
    	propNet.getInitProposition().setValue(true);
    	return getStateFromBase();
    }

    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
    	//TODO: FIND ACTIONS
        return null;
    }

    /**
     * Computes the legal moves for role in state.
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {

    	// These next two lines are written over and over, maybe try to
    	// find a way to save time and not call this multiple times? @junwon
        clearPropNet();
        markBases(state);


        // Set of legal input propositions for this role
        List<Proposition> legals = new ArrayList<Proposition> (propNet.getLegalPropositions().get(role));
        List<Move> actions = new ArrayList<Move>();

        int len = legals.size();
        for (int i = 0; i < len; i++) {
        	if (propMarkP(legals.get(i))) {
        		actions.add(getMoveFromProposition(legals.get(i)));
        	}
        }
        return actions;
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
    	/* Ah crap I have no idea what this is
		markActions(state);
		markBases(state);
    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
    	MachineState nextState = new MachineState();
		for (int i = 0; i < bases.size(); i++) {
			next = propMarkP(bases[i].source.source)};
		return nexts
		*/
      // TODO: Compute the next state.
        return null;
		/*
		Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(p.getSingleInput().getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
		 */
    }

    /**
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    public List<Proposition> getOrdering()
    {
        // List to contain the topological ordering.
        List<Proposition> order = new LinkedList<Proposition>();

        // All of the components in the PropNet
        List<Component> components = new ArrayList<Component>(propNet.getComponents());

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        // TODO: Compute the topological ordering.

        return order;
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    /* Helper methods */

    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(p.getSingleInput().getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }
}