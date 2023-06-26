package hu.mondokm.ltlverif.xsts;

import hu.bme.mit.theta.analysis.pred.PredAbstractors;
import hu.bme.mit.theta.analysis.pred.PredPrec;
import hu.bme.mit.theta.analysis.pred.PredState;
import hu.bme.mit.theta.analysis.pred.PredTransFunc;
import hu.bme.mit.theta.core.stmt.Stmt;
import hu.bme.mit.theta.solver.z3.Z3SolverFactory;
import hu.mondokm.ltlverif.abstractor.ProductState;
import hu.mondokm.ltlverif.abstractor.SUT;
import hu.mondokm.ltlverif.buchi.BuchiAction;
import hu.mondokm.ltlverif.buchi.BuchiState;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;

public class XSTSSUT implements SUT {

    private XSTS xsts;
    private HashSet<Transition> transitions;
    private static PredTransFunc predTransFunc = PredTransFunc.create(PredAbstractors.booleanSplitAbstractor(Z3SolverFactory.getInstance().createSolver()));

    public XSTSSUT(XSTS xsts) {
        this.xsts = xsts;
        transitions = new HashSet<Transition>();
        for (List<Stmt> stmts : xsts.getTransitions().getStmts()) {
            transitions.add(new Transition(stmts));
        }
    }

    @Override
    public ProductState getInitialState(BuchiState starting) {
        return new XSTSProductState(null, this, PredState.of(True()), starting, null);
    }

    @Override
    public boolean isFinalState(ProductState prod) {
        return false;
    }

    public HashSet<Transition> getTransitions() {
        return transitions;
    }

    public HashSet<Transition> getInitialTransitions() {
        HashSet<Transition> initTransitions = new HashSet<Transition>();
        for (List<Stmt> stmts : xsts.getInitAction().getStmts()) {
            initTransitions.add(new Transition(stmts));
        }
        return initTransitions;
    }

    public HashSet<ProductState> nextStates(XSTSProductState curr, Transition transition, PredPrec precision) {
        HashSet<ProductState> states = new HashSet<ProductState>();
        Collection<PredState> collection = predTransFunc.getSuccStates(curr.getPredState(), transition, precision);
        for (PredState state : collection) {
            if (!state.isBottom()) {
                for (BuchiAction action : curr.getBuchiState().getActions()) {
                    Collection<PredState> innerCollection = predTransFunc.getSuccStates(state, action, precision);
                    for (PredState innerState : innerCollection) {
                        if (!innerState.isBottom())
                            states.add(new XSTSProductState(transition, this, innerState, action.getTarget(), action));
//                        System.out.println(transition.getStmts() + " " + innerState + " " + action.getTarget().getId());
                    }
                }
            }
        }
        return states;
    }

}